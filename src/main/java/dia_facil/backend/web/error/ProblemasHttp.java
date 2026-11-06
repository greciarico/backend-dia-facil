package dia_facil.backend.web.error;

import dia_facil.backend.service.auth.AutenticacionCuidadorService.Motivo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

public final class ProblemasHttp {
    private ProblemasHttp() {}

    public static ProblemDetail crear(int status, String codigo, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(path));
        problem.setProperty("codigo", codigo);
        return problem;
    }

    public static ResponseEntity<ProblemDetail> respuesta(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .cacheControl(CacheControl.noStore()).body(problem);
    }

    public static ResponseEntity<ProblemDetail> rechazo(Motivo motivo, String path) {
        int status = switch (motivo) {
            case CODIGO_INEXISTENTE, CODIGO_INCORRECTO, CODIGO_VENCIDO, CODIGO_USADO, COMPROBANTE_INVALIDO -> 422;
            case CUENTA_EXISTENTE -> 409;
            case LIMITE_INTENTOS, REENVIO_PREMATURO -> 429;
            case CUENTA_INACTIVA -> 403;
            default -> 500;
        };
        String detail = switch (motivo) {
            case CODIGO_INEXISTENTE -> "Solicita un código antes de verificarlo.";
            case CODIGO_INCORRECTO -> "El código ingresado no es correcto.";
            case CODIGO_VENCIDO -> "El código ha vencido. Solicita otro código.";
            case CODIGO_USADO -> "El código ya fue utilizado. Solicita otro código.";
            case LIMITE_INTENTOS -> "Se alcanzó el límite de intentos. Solicita otro código.";
            case REENVIO_PREMATURO -> "Espera antes de solicitar otro código.";
            case CUENTA_INACTIVA -> "La cuenta está inactiva.";
            case COMPROBANTE_INVALIDO -> "La verificación de registro ya no es válida. Solicita y verifica otro código.";
            case CUENTA_EXISTENTE -> "La cuenta ya existe. Solicita y verifica otro código para acceder.";
            default -> "No se pudo completar la operación.";
        };
        var problem = crear(status, status == 500 ? "ERROR_INTERNO" : motivo.name(), detail, path);
        if (motivo == Motivo.REENVIO_PREMATURO) {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.RETRY_AFTER,
                    Integer.toString(dia_facil.backend.service.auth.PoliticaCodigoAcceso.REENVIO_EN_SEGUNDOS)).body(problem);
        }
        return respuesta(problem);
    }

    public static void accesoDenegado(ObjectMapper mapper, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        var problem = crear(HttpStatus.FORBIDDEN.value(), "ACCESO_DENEGADO",
            "Esta ruta no está habilitada.", request.getRequestURI());
        // Explicitly flatten extensions also outside MVC's ProblemDetail message converter.
        var body = new LinkedHashMap<String, Object>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", problem.getInstance().toString());
        body.putAll(problem.getProperties());
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        mapper.writeValue(response.getOutputStream(), body);
    }
    public static void escribir(HttpServletResponse response, ObjectMapper mapper, String path, int status, String codigo, String mensaje) throws IOException {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "about:blank"); body.put("title", HttpStatus.resolve(status) == null ? "Error" : HttpStatus.resolve(status).getReasonPhrase());
        body.put("status", status); body.put("detail", mensaje); body.put("instance", path); body.put("codigo", codigo);
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"); mapper.writeValue(response.getOutputStream(), body);
    }
}
