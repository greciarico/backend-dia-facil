package dia_facil.backend.service.actividades;

public record ActividadResumen(Integer idActividad, String nombre, String hora, String codigoIcono) {
    public ActividadResumen(Integer id, String nombre) { this(id, nombre, (String) null, null); }
    public ActividadResumen(Integer id, String nombre, java.time.LocalTime hora, String codigo) {
        this(id, nombre, hora.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), codigo);
    }
}
