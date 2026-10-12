package dia_facil.backend.web.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo debe ser válido.")
        @Size(max = 150, message = "El correo debe tener como máximo 150 caracteres.") String email) {
    public SolicitarCodigoRequest {
        email = email == null ? null : email.strip();
    }
}
