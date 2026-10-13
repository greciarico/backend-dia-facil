package dia_facil.backend.web.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletarRegistroRequest(
    @NotBlank @Size(max = 2048) String comprobanteRegistro,
    @NotBlank @Size(max = 100) String nombre,
    @Size(max = 32) String avatarId) {
    public CompletarRegistroRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
    @Override public String toString() { return "CompletarRegistroRequest[contenido reservado]"; }
}
