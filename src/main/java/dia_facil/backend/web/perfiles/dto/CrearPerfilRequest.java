package dia_facil.backend.web.perfiles.dto;

/** Ownership and internal fields are never accepted as command arguments. */
public record CrearPerfilRequest(String nombre, String avatar) {
    @Override public String toString() { return "CrearPerfilRequest[contenido reservado]"; }
}
