package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.enums.EstadoEjecucion;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record ActividadAgenda(Integer idActividad, String nombre, String hora, String codigoIcono, String estado) {
    public ActividadAgenda(Integer id, String nombre, LocalTime hora, String codigoIcono, EstadoEjecucion estado) {
        this(id, nombre, hora.format(DateTimeFormatter.ofPattern("HH:mm")), codigoIcono, estado == null ? "PENDIENTE" : estado.name());
    }
}
