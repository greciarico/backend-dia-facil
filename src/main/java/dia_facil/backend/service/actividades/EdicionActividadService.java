package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.perfiles.RechazoPerfil;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EdicionActividadService {
    public record PasoEntrada(String descripcion, Integer idArasaac, Integer conservarPictogramaDePaso) {}
    public record Entrada(Integer revision, String nombre, String hora, String codigoIcono, List<PasoEntrada> pasos) {}
    public record Paso(Integer idPasoActividad, String descripcion, PlantillaActividadService.Pictograma pictograma) {}
    public record Detalle(Integer idActividad, int revision, String nombre, String hora, String codigoIcono,
                          String estadoHoy, List<Paso> pasos) {}

    public record Eliminacion(Integer idActividad, String resultado, String estadoHoy) {}

    private final EntityManager em;
    private final PerfilUsuarioRepository perfiles;
    private final IconoActividadRepository iconos;
    private final CrearActividadService creacion;
    private final CreacionActividadTransaccionalService pasos;
    private final Clock clock;
    private final RegistroSeguimiento seguimiento;
    private final TransactionTemplate escritura;

    public EdicionActividadService(EntityManager em, PerfilUsuarioRepository perfiles, IconoActividadRepository iconos,
            CrearActividadService creacion, CreacionActividadTransaccionalService pasos, PlatformTransactionManager manager,
            @Qualifier("agendaUsuarioClock") Clock clock, RegistroSeguimiento seguimiento) {
        this.em = em;
        this.perfiles = perfiles;
        this.iconos = iconos;
        this.creacion = creacion;
        this.pasos = pasos;
        this.clock = clock;
        this.seguimiento = seguimiento;
        escritura = new TransactionTemplate(manager);
        escritura.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(readOnly = true)
    public Detalle consultar(Integer cuidador, Integer perfil, Integer id) {
        var actividad = propia(cuidador, perfil, id);
        return detalle(actividad, pasosVigentes(actividad));
    }

    public Detalle editar(Integer cuidador, Integer perfil, Integer id, Entrada entrada) {
        propia(cuidador, perfil, id);
        if (entrada.revision() == null || entrada.revision() < 1) throw RechazoActividad.datos("revision", "Vuelve a consultar la actividad.");
        var datos = new CrearActividad(entrada.nombre(), entrada.hora(), entrada.codigoIcono(), entrada.pasos() == null ? null :
            entrada.pasos().stream().map(p -> p == null ? null : new CrearActividad.Paso(p.descripcion(), p.idArasaac())).toList()).validado();
        var metadata = creacion.resolverPictogramas(datos);
        for (int intento = 0; intento < 3; intento++) {
            try {
                return escritura.execute(status -> {
                    perfiles.bloquear(perfil, cuidador, EstadoPerfil.ACTIVO).orElseThrow(RechazoPerfil::noEncontrado);
                    var actividad = propia(cuidador, perfil, id);
                    em.refresh(actividad, LockModeType.PESSIMISTIC_WRITE);
                    if (actividad.getEstado() != EstadoActividad.ACTIVA) throw RechazoActividad.noEncontrada();
                    if (actividad.getRevision() != entrada.revision())
                        throw new RechazoActividad("ACTIVIDAD_MODIFICADA", "La actividad cambió en otra sesión.", null);
                    return guardar(actividad, datos, entrada.pasos(), metadata);
                });
            } catch (RuntimeException error) {
                if (!CrearActividadService.colisionArasaac(error)) throw error;
            }
        }
        throw new RechazoActividad("CONFLICTO_CREACION", "No pudimos guardar los cambios. Inténtalo de nuevo.", null);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Eliminacion eliminar(Integer cuidador, Integer perfil, Integer id, int revision) {
        if (revision < 1) throw RechazoActividad.datos("revision", "Vuelve a consultar la actividad.");
        perfiles.bloquear(perfil, cuidador, EstadoPerfil.ACTIVO).orElseThrow(RechazoActividad::noEncontrada);
        var actividad = propia(cuidador, perfil, id);
        em.refresh(actividad, LockModeType.PESSIMISTIC_WRITE);
        if (actividad.getEstado() != EstadoActividad.ACTIVA) throw RechazoActividad.noEncontrada();
        if (actividad.getRevision() != revision)
            throw new RechazoActividad("ACTIVIDAD_MODIFICADA", "La actividad cambió en otra sesión.", null);
        var fecha = LocalDate.now(clock);
        String hoy = estadoHoy(actividad, fecha);
        boolean conservar = existe("select e.idEjecucionActividad from EjecucionActividad e where e.actividad = :actividad", actividad)
            || existe("select g.idProgresoPaso from ProgresoPaso g where g.pasoActividad.actividad = :actividad", actividad)
            || existe("""
                select s.idSolicitudAyuda from SolicitudAyuda s
                left join s.pasoActividad p left join s.ejecucionActividad e
                where s.actividad = :actividad or p.actividad = :actividad or e.actividad = :actividad
                """, actividad);
        seguimiento.retirar(actividad, fecha);
        if (conservar) actividad.setEstado(EstadoActividad.INACTIVA);
        else em.remove(actividad);
        em.flush();
        return new Eliminacion(id, conservar ? "RETIRADA" : "BORRADA", hoy);
    }

    private boolean existe(String jpql, Actividad actividad) {
        return !em.createQuery(jpql, Integer.class).setParameter("actividad", actividad)
            .setMaxResults(1).getResultList().isEmpty();
    }

    private Detalle guardar(Actividad actividad, CrearActividad datos, List<PasoEntrada> entrada, Map<Integer, PictogramaArasaac> metadata) {
        var anteriores = pasosVigentes(actividad);
        var icono = iconos.findByCodigo(datos.codigoIcono()).orElseThrow(RechazoActividad::icono);
        var locales = pasos.resolverLocales(datos, metadata);
        var imagenes = new ArrayList<Pictograma>();
        boolean igual = Objects.equals(actividad.getNombre(), datos.nombre()) && Objects.equals(actividad.getHora(), datos.horaLocal())
            && actividad.getIconoActividad() != null && Objects.equals(actividad.getIconoActividad().getIdIconoActividad(), icono.getIdIconoActividad()) && anteriores.size() == entrada.size();
        for (int i = 0; i < entrada.size(); i++) {
            var paso = entrada.get(i);
            var imagen = locales.get(paso.idArasaac());
            if (paso.conservarPictogramaDePaso() != null) {
                if (paso.idArasaac() != null) throw RechazoActividad.pictograma();
                imagen = anteriores.stream().filter(p -> paso.conservarPictogramaDePaso().equals(p.getIdPasoActividad()))
                    .map(PasoActividad::getPictograma).filter(Objects::nonNull).filter(p -> p.getIdArasaac() == null)
                    .findFirst().orElseThrow(RechazoActividad::pictograma);
            }
            imagenes.add(imagen);
            igual = igual && Objects.equals(anteriores.get(i).getPictograma(), imagen)
                && anteriores.get(i).getDescripcion().equals(datos.pasos().get(i).descripcion());
        }
        if (igual) return detalle(actividad, anteriores);
        actividad.setRevision(actividad.getRevision() + 1);
        actividad.setNombre(datos.nombre());
        actividad.setHora(datos.horaLocal());
        actividad.setIconoActividad(icono);
        em.persist(VersionActividad.de(actividad));
        var nuevos = new ArrayList<PasoActividad>();
        for (int i = 0; i < entrada.size(); i++) {
            var paso = PasoActividad.crear(actividad, i + 1, datos.pasos().get(i).descripcion(), imagenes.get(i));
            em.persist(paso);
            nuevos.add(paso);
        }
        em.flush();
        seguimiento.guardar(actividad, nuevos);
        return detalle(actividad, nuevos);
    }

    private Actividad propia(Integer cuidador, Integer perfil, Integer id) {
        return em.createQuery("select a from Actividad a join a.perfilUsuario p where a.idActividad=:id and p.idPerfilUsuario=:perfil and p.idCuidador=:cuidador and p.estado=:perfilActivo and a.estado=:activa", Actividad.class)
            .setParameter("id", id).setParameter("perfil", perfil).setParameter("cuidador", cuidador)
            .setParameter("perfilActivo", EstadoPerfil.ACTIVO).setParameter("activa", EstadoActividad.ACTIVA)
            .getResultList().stream().findFirst().orElseThrow(RechazoActividad::noEncontrada);
    }

    private List<PasoActividad> pasosVigentes(Actividad actividad) {
        return em.createQuery("select p from PasoActividad p left join fetch p.pictograma where p.actividad=:actividad and p.revision=:revision and p.activo=true order by p.orden,p.idPasoActividad", PasoActividad.class)
            .setParameter("actividad", actividad).setParameter("revision", actividad.getRevision()).getResultList();
    }

    private String estadoHoy(Actividad actividad, LocalDate fecha) {
        return em.createQuery("select e.estado from EjecucionActividad e where e.actividad=:actividad and e.fecha=:fecha", EstadoEjecucion.class)
            .setParameter("actividad", actividad).setParameter("fecha", fecha)
            .getResultStream().findFirst().map(Enum::name).orElse("PENDIENTE");
    }

    private Detalle detalle(Actividad actividad, List<PasoActividad> pasosActuales) {
        var estado = estadoHoy(actividad, LocalDate.now(clock));
        var resumen = new ActividadResumen(actividad.getIdActividad(), actividad.getNombre(), actividad.getHora(),
            actividad.getIconoActividad() == null ? null : actividad.getIconoActividad().getCodigo());
        return new Detalle(resumen.idActividad(), actividad.getRevision(), resumen.nombre(), resumen.hora(), resumen.codigoIcono(),
            estado, pasosActuales.stream().map(p -> {
                var imagen = p.getPictograma();
                return new Paso(p.getIdPasoActividad(), p.getDescripcion(), imagen == null ? null :
                    PlantillaActividadService.Pictograma.de(imagen.getIdPictograma(), imagen.getNombre(), imagen.getIdArasaac()));
            }).toList());
    }
}
