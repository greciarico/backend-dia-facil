package dia_facil.backend.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificarCodigoResponse(String estado, Integer idCuidador, String comprobanteRegistro) {}
