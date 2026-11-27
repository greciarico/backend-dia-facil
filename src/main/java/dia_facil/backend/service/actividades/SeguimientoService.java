package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.perfiles.RechazoPerfil;
import dia_facil.backend.service.actividades.RegistroSeguimiento.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SeguimientoService {
    public record Resumen(int total, int completadas, int enProgreso, int pendientes, Integer porcentaje) {}
    public record ActividadDia(int idActividad, int revision, String nombre, String hora, String codigoIcono,
                               String estado, int pasosCompletados, int totalPasos) {}
    public record Paso(int posicion, String descripcion, String estado, PlantillaActividadService.Pictograma pictograma) {}
    public record Dia(LocalDate fecha, LocalDate fechaMinima, LocalDate fechaActual, String zonaHoraria,
                      long actualizarEnSegundos, boolean tieneActividadesActivas, boolean tieneHistorial,
                      String cobertura, Resumen resumen, List<ActividadDia> actividades) {}
    public record Detalle(LocalDate fecha, ActividadDia actividad, List<Paso> pasos) {}
    private record Fecha(PerfilUsuario perfil, LocalDate consultada, LocalDate minima, LocalDate hoy, Instant ahora) {}
    private record Fila(Definicion definicion, EstadoEjecucion estado, ModoFinalizacion modo, Set<Integer> completados) {
        ActividadDia dto() {
            return new ActividadDia(definicion.idActividad(), definicion.revision(), definicion.nombre(),
                definicion.hora().format(DateTimeFormatter.ofPattern("HH:mm")), definicion.codigoIcono(),
                estado == null ? "PENDIENTE" : estado.name(), (int) definicion.pasos().stream()
                    .filter(p -> completados.contains(p.idPasoActividad())).count(), definicion.pasos().size());
        }
    }

    private final EntityManager em;
    private final RegistroSeguimiento registro;
    private final Clock reloj;
    private final ZoneId zonaCreacion;

    public SeguimientoService(EntityManager em, RegistroSeguimiento registro, @Qualifier("agendaUsuarioClock") Clock reloj,
            @Value("${diafacil.seguimiento.zona-fecha-creacion:${diafacil.agenda.zone:America/Lima}}") String zonaCreacion) {
        this.em = em;
        this.registro = registro;
        this.reloj = reloj;
        this.zonaCreacion = ZoneId.of(zonaCreacion);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dia consultar(Integer cuidador, Integer perfil, String fecha) {
        var dia = fecha(cuidador, perfil, fecha);
        boolean completa = !dia.consultada().isBefore(registro.inicio());
        var activas = vigentes(perfil);
        var filas = filas(dia, completa, activas).stream().map(Fila::dto).toList();
        boolean historial = registro.tieneRutinaHistorica(perfil, dia.hoy()) || !em.createQuery("""
            select e.idEjecucionActividad from EjecucionActividad e where e.actividad.perfilUsuario.idPerfilUsuario=:perfil
            """, Integer.class).setParameter("perfil", perfil).setMaxResults(1).getResultList().isEmpty();
        long segundos = Math.max(1, (Duration.between(dia.ahora(), dia.hoy().plusDays(1).atStartOfDay(reloj.getZone()).toInstant()).toMillis() + 999) / 1000);
        return new Dia(dia.consultada(), dia.minima(), dia.hoy(), reloj.getZone().getId(), segundos, !activas.isEmpty(),
            historial, completa ? "COMPLETA" : "PARCIAL", completa ? resumir(filas) : null, filas);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detalle detalle(Integer cuidador, Integer perfil, Integer actividad, String fecha) {
        var dia = fecha(cuidador, perfil, fecha);
        var fila = filas(dia, !dia.consultada().isBefore(registro.inicio()), dia.consultada().equals(dia.hoy()) ? vigentes(perfil) : List.of())
            .stream().filter(item -> item.definicion.idActividad() == actividad).findFirst().orElseThrow(RechazoActividad::noEncontrada);
        var pasos = new ArrayList<Paso>();
        for (var paso : fila.definicion.pasos()) pasos.add(new Paso(pasos.size() + 1, paso.descripcion(),
            fila.completados.contains(paso.idPasoActividad()) ? "COMPLETADO" : fila.modo == ModoFinalizacion.DIRECTA ? "SIN_REGISTRO" : "PENDIENTE", paso.imagen()));
        return new Detalle(dia.consultada(), fila.dto(), List.copyOf(pasos));
    }

    static Resumen resumir(List<ActividadDia> filas) {
        int total = filas.size();
        int cerradas = (int) filas.stream().filter(a -> a.estado().equals("COMPLETADA")).count();
        int iniciadas = (int) filas.stream().filter(a -> a.estado().equals("EN_PROGRESO")).count();
        return new Resumen(total, cerradas, iniciadas, total - cerradas - iniciadas,
            total == 0 ? null : (int) Math.round(100.0 * cerradas / total));
    }

    private Fecha fecha(Integer cuidador, Integer perfil, String consulta) {
        var persona = em.createQuery("""
            select p from PerfilUsuario p where p.idPerfilUsuario=:perfil and p.idCuidador=:cuidador and p.estado=:estado
            """, PerfilUsuario.class).setParameter("perfil", perfil).setParameter("cuidador", cuidador)
            .setParameter("estado", EstadoPerfil.ACTIVO).getResultList().stream().findFirst().orElseThrow(RechazoPerfil::noEncontrado);
        var ahora = reloj.instant();
        var hoy = ahora.atZone(reloj.getZone()).toLocalDate();
        var minima = persona.getFechaCreacion().atZone(zonaCreacion).withZoneSameInstant(reloj.getZone()).toLocalDate();
        try {
            if (consulta != null && !consulta.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("Formato");
            var fecha = consulta == null ? hoy : LocalDate.parse(consulta);
            if (fecha.isBefore(minima) || fecha.isAfter(hoy)) throw new DateTimeException("Rango");
            return new Fecha(persona, fecha, minima, hoy, ahora);
        } catch (DateTimeException error) {
            throw RechazoActividad.datos("fecha", "Selecciona una fecha entre la creación del perfil y hoy.");
        }
    }

    private List<Actividad> vigentes(int perfil) {
        return em.createQuery("""
            select a from Actividad a left join fetch a.iconoActividad
            where a.perfilUsuario.idPerfilUsuario=:perfil and a.estado=:estado
            """, Actividad.class).setParameter("perfil", perfil).setParameter("estado", EstadoActividad.ACTIVA).getResultList();
    }

    private List<Fila> filas(Fecha dia, boolean completa, List<Actividad> activas) {
        var perfil = dia.perfil().getIdPerfilUsuario();
        var ejecuciones = em.createQuery("""
            select e from EjecucionActividad e join fetch e.actividad a left join fetch a.iconoActividad
            where a.perfilUsuario.idPerfilUsuario=:perfil and e.fecha=:fecha
            """, EjecucionActividad.class).setParameter("perfil", perfil).setParameter("fecha", dia.consultada()).getResultList();
        boolean hoy = dia.consultada().equals(dia.hoy());
        var porActividad = new HashMap<Integer, Fila>();
        if (completa && !hoy)
            for (var def : registro.delDia(perfil, dia.consultada())) porActividad.put(def.idActividad(), new Fila(def, null, null, Set.of()));
        if (completa && hoy)
            for (var a : activas) porActividad.put(a.getIdActividad(), new Fila(definicion(a.getIdActividad(), a.getRevision(),
                a.getNombre(), a.getHora(), a.getIconoActividad()), null, null, Set.of()));
        for (var e : ejecuciones) {
            var version = VersionActividad.deEjecucion(em, e);
            var id = e.getActividad().getIdActividad();
            var hechos = em.createQuery("""
                select p.pasoActividad.idPasoActividad from ProgresoPaso p
                where p.ejecucionActividad=:ejecucion and p.completado=true
                """, Integer.class).setParameter("ejecucion", e).getResultList();
            porActividad.put(id, new Fila(definicion(id, e.getRevision(), version.getNombre(), version.getHora(), version.getIcono()),
                e.getEstado(), e.getModoFinalizacion(), new HashSet<>(hechos)));
        }
        return porActividad.values().stream().sorted(Comparator.comparing((Fila f) -> f.definicion.hora())
            .thenComparingInt(f -> f.definicion.idActividad())).collect(Collectors.toUnmodifiableList());
    }

    private Definicion definicion(int actividad, int revision, String nombre, LocalTime hora, IconoActividad icono) {
        var pasos = em.createQuery("""
            select p from PasoActividad p left join fetch p.pictograma
            where p.actividad.idActividad=:actividad and p.revision=:revision and p.activo=true order by p.orden,p.idPasoActividad
            """, PasoActividad.class).setParameter("actividad", actividad).setParameter("revision", revision).getResultList();
        return new Definicion(actividad, revision, nombre, hora, icono == null ? null : icono.getCodigo(),
            pasos.stream().map(PasoBase::de).toList());
    }
}
