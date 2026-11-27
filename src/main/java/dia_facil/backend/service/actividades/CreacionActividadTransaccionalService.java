package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.enums.EstadoActividad;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.perfiles.RechazoPerfil;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CreacionActividadTransaccionalService {
    private final PerfilUsuarioRepository perfiles;
    private final IconoActividadRepository iconos;
    private final PictogramaRepository pictogramas;
    private final EntityManager em;
    private final ActividadRepository actividades;
    private final RegistroSeguimiento seguimiento;
    public CreacionActividadTransaccionalService(PerfilUsuarioRepository perfiles, IconoActividadRepository iconos,
            PictogramaRepository pictogramas, EntityManager em, ActividadRepository actividades, RegistroSeguimiento seguimiento) {
        this.perfiles = perfiles; this.iconos = iconos; this.pictogramas = pictogramas; this.em = em; this.actividades = actividades; this.seguimiento = seguimiento;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public ActividadResumen crear(Integer cuidador, Integer perfilId, CrearActividad datos, Map<Integer, PictogramaArasaac> metadata) {
        var perfil = perfiles.bloquear(perfilId, cuidador, EstadoPerfil.ACTIVO)
            .orElseThrow(RechazoPerfil::noEncontrado);
        if (actividades.countByPerfilUsuario_IdPerfilUsuarioAndEstado(perfilId, EstadoActividad.ACTIVA)
                >= LimitesActividad.MAXIMO_ACTIVIDADES_ACTIVAS) throw RechazoActividad.limite();
        var icono = iconos.findByCodigo(datos.codigoIcono()).orElseThrow(RechazoActividad::icono);
        var locales = resolverLocales(datos, metadata);
        var actividad = Actividad.crear(perfil, datos.nombre(), datos.horaLocal(), icono);
        em.persist(actividad);
        em.persist(VersionActividad.de(actividad));
        var pasos = new ArrayList<PasoActividad>();
        for (int i = 0; i < datos.pasos().size(); i++) {
            var paso = datos.pasos().get(i);
            var entidad = PasoActividad.crear(actividad, i + 1, paso.descripcion(), locales.get(paso.idArasaac()));
            em.persist(entidad);
            pasos.add(entidad);
        }
        em.flush();
        seguimiento.guardar(actividad, pasos);
        return new ActividadResumen(actividad.getIdActividad(), actividad.getNombre(), datos.hora(), icono.getCodigo());
    }

    Map<Integer, Pictograma> resolverLocales(CrearActividad datos, Map<Integer, PictogramaArasaac> metadata) {
        var ids = datos.pasos().stream().map(CrearActividad.Paso::idArasaac).filter(Objects::nonNull).distinct().sorted().toList();
        var locales = new HashMap<Integer, Pictograma>();
        if (!ids.isEmpty()) pictogramas.findByIdArasaacIn(ids).forEach(p -> locales.put(p.getIdArasaac(), p));
        for (var id : ids) {
            if (locales.containsKey(id)) continue;
            var externo = metadata.get(id);
            if (externo == null || !id.equals(externo.idArasaac())) throw RechazoActividad.pictograma();
            var nuevo = Pictograma.crearArasaac(externo.nombre(), id);
            em.persist(nuevo); locales.put(id, nuevo);
        }
        return locales;
    }
}
