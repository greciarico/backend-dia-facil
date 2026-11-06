package dia_facil.backend.service.perfiles;

/** Read model: never exposes linking codes, ownership or activity data. */
public record PerfilResumen(Integer idPerfilUsuario, String nombre, String avatar) {}
