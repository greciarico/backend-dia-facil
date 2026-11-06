package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.enums.EstadoEjecucion;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Definition and today's state only; no steps, pictograms or execution graph. */
public record ActividadDetalle(Integer idActividad, String nombre, String hora, String codigoIcono,
    String estado, boolean guiaInicialCompletada) {
    public ActividadDetalle(Integer id, String nombre, LocalTime hora, String codigoIcono,
        EstadoEjecucion estado, Boolean guiaInicialCompletada) {
        this(id, nombre, hora.format(DateTimeFormatter.ofPattern("HH:mm")), codigoIcono,
            estado == null ? "PENDIENTE" : estado.name(), Boolean.TRUE.equals(guiaInicialCompletada));
    }
}
