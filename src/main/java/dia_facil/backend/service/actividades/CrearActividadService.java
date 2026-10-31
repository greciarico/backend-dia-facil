package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.perfiles.PerfilUsuarioService;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;

@Service
public class CrearActividadService {
    private final PerfilUsuarioService perfiles;
    private final IconoActividadRepository iconos;
    private final PictogramaRepository pictogramas;
    private final PictogramaArasaacService arasaac;
    private final CreacionActividadTransaccionalService transacciones;
    public CrearActividadService(PerfilUsuarioService perfiles, IconoActividadRepository iconos, PictogramaRepository pictogramas,
            PictogramaArasaacService arasaac, CreacionActividadTransaccionalService transacciones) {
        this.perfiles = perfiles; this.iconos = iconos; this.pictogramas = pictogramas; this.arasaac = arasaac; this.transacciones = transacciones;
    }
    public ActividadResumen crear(Integer cuidador, Integer perfil, CrearActividad entrada) {
        perfiles.detalle(cuidador, perfil); // No external request for another caregiver's profile.
        var datos = entrada.validado();
        if (iconos.findByCodigo(datos.codigoIcono()).isEmpty()) throw RechazoActividad.icono();
        var ids = new TreeSet<Integer>();
        datos.pasos().forEach(p -> { if (p.idArasaac() != null) ids.add(p.idArasaac()); });
        var existentes = new HashSet<Integer>();
        if (!ids.isEmpty()) pictogramas.findByIdArasaacIn(ids).forEach(p -> existentes.add(p.getIdArasaac()));
        var metadata = new HashMap<Integer, PictogramaArasaac>();
        for (var id : ids) if (!existentes.contains(id)) metadata.put(id, arasaac.resolver(id));
        for (int intento = 0; intento < 3; intento++) {
            try { return transacciones.crear(cuidador, perfil, datos, metadata); }
            catch (RuntimeException error) { if (!colisionArasaac(error)) throw error; }
        }
        throw new RechazoActividad("CONFLICTO_CREACION", "No pudimos completar la creación. Inténtalo de nuevo.", null);
    }
    static boolean colisionArasaac(Throwable error) {
        for (var causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException c && c.getConstraintName() != null) {
                var nombre = c.getConstraintName().replace("`", "").replace("\"", "");
                if (nombre.equals("uq_pictograma_arasaac") || nombre.endsWith(".uq_pictograma_arasaac")) return true;
            }
        }
        return false;
    }
}
