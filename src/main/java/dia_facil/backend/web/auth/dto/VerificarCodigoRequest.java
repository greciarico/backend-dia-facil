package dia_facil.backend.web.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerificarCodigoRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo debe ser válido.")
        @Size(max = 150, message = "El correo debe tener como máximo 150 caracteres.") String email,
        @NotBlank(message = "El código es obligatorio.")
        @Pattern(regexp = "[0-9]{6}", message = "Debe contener exactamente 6 dígitos.") String codigo) {
    public VerificarCodigoRequest {
        email = email == null ? null : email.strip();
    }
}
