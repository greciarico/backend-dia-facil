package dia_facil.backend.web.auth.dto;

public record CompletarRegistroResponse(String estado, Integer idCuidador,
    dia_facil.backend.service.auth.SesionCuidadorService.CuidadorResumen cuidador,
    dia_facil.backend.service.auth.SesionCuidadorService.Tokens sesion) {
    @Override public String toString() { return "CompletarRegistroResponse[contenido reservado]"; }
}
