package dia_facil.backend.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificarCodigoResponse(String estado, Integer idCuidador, String comprobanteRegistro,
    dia_facil.backend.service.auth.SesionCuidadorService.CuidadorResumen cuidador,
    dia_facil.backend.service.auth.SesionCuidadorService.Tokens sesion) {
    @Override public String toString() { return "VerificarCodigoResponse[contenido reservado]"; }
}
