package dia_facil.backend.service.perfiles;

/** Read model: never exposes linking codes, ownership or activity data. */
public record PerfilResumen(Integer idPerfilUsuario, String nombre, String avatar, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Boolean tieneFoto, Integer revision) {
    public PerfilResumen(Integer id, String nombre, String avatar, Boolean tieneFoto) { this(id, nombre, avatar, tieneFoto, 1); }
    public PerfilResumen(Integer id, String nombre, String avatar) { this(id, nombre, avatar, null, 1); }
}
