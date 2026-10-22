package dia_facil.backend.web.error;

import dia_facil.backend.service.auth.AutenticacionCuidadorService.RechazoAutenticacion;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.Map;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class AutenticacionExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(dia_facil.backend.service.actividades.RechazoActividad.class)
    public ResponseEntity<ProblemDetail> actividadInvalida(dia_facil.backend.service.actividades.RechazoActividad error, HttpServletRequest request) {
        var problema = ProblemasHttp.crear("CONFLICTO_CREACION".equals(error.codigo()) ? 409 : 400,
            error.codigo(), error.getMessage(), request.getRequestURI());
        if (error.campo() != null) problema.setProperty("errores", java.util.List.of(Map.of("campo", error.campo(), "mensaje", error.getMessage())));
        return ProblemasHttp.respuesta(problema);
    }
    @ExceptionHandler(dia_facil.backend.service.actividades.ArasaacClient.NoDisponible.class)
    public ResponseEntity<ProblemDetail> pictogramasNoDisponibles(HttpServletRequest request) {
        return ProblemasHttp.respuesta(ProblemasHttp.crear(503, "PICTOGRAMAS_NO_DISPONIBLES",
            "No pudimos consultar los pictogramas. Inténtalo de nuevo.", request.getRequestURI()));
    }
    @ExceptionHandler(dia_facil.backend.service.perfiles.RechazoPerfil.class)
    public ResponseEntity<ProblemDetail> perfilInvalido(dia_facil.backend.service.perfiles.RechazoPerfil error, HttpServletRequest request) {
        int status = switch (error.codigo()) {
            case "PERFIL_NO_ENCONTRADO" -> 404;
            case "LIMITE_PERFILES_ALCANZADO" -> 409;
            case "SERVICIO_NO_DISPONIBLE" -> 503;
            default -> 400;
        };
        var problem = ProblemasHttp.crear(status, error.codigo(), error.getMessage(), request.getRequestURI());
        if (error.campo() != null) problem.setProperty("errores", java.util.List.of(Map.of("campo", error.campo(), "mensaje", error.getMessage())));
        return ProblemasHttp.respuesta(problem);
    }
    @ExceptionHandler(dia_facil.backend.service.auth.SesionInvalidada.class)
    public ResponseEntity<ProblemDetail> sesionInvalida(dia_facil.backend.service.auth.SesionInvalidada error, HttpServletRequest request) {
        return ProblemasHttp.respuesta(ProblemasHttp.crear(401, error.renovable() ? "SESION_REQUIERE_RENOVACION" : "SESION_INVALIDA",
            "Tu sesión terminó. Verifica tu correo para volver a entrar.", request.getRequestURI()));
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemasHttp.crear(400, "SOLICITUD_INVALIDA", "Revisa los datos ingresados.", path(request));
        var errores = ex.getBindingResult().getFieldErrors().stream()
            .sorted(Comparator.comparing(org.springframework.validation.FieldError::getField))
            .map(error -> Map.of("campo", error.getField(), "mensaje", mensaje(error.getField(), error.getCode())))
            .distinct().toList();
        problem.setProperty("errores", errores);
        return objeto(ProblemasHttp.respuesta(problem));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return objeto(ProblemasHttp.respuesta(ProblemasHttp.crear(status.value(),
            status.is5xxServerError() ? "ERROR_INTERNO" : "SOLICITUD_INVALIDA",
            status.is5xxServerError() ? "No se pudo completar la operación." : "Solicitud no válida.", path(request))));
    }

    @ExceptionHandler(RechazoAutenticacion.class)
    public ResponseEntity<ProblemDetail> rechazo(RechazoAutenticacion error, HttpServletRequest request) {
        return ProblemasHttp.rechazo(error.getMotivo(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> entradaInvalida(HttpServletRequest request) {
        return ProblemasHttp.respuesta(ProblemasHttp.crear(400, "SOLICITUD_INVALIDA",
            "Revisa los datos ingresados.", request.getRequestURI()));
    }

    @ExceptionHandler({MailAuthenticationException.class, MailSendException.class,
        DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ResponseEntity<ProblemDetail> indisponible(HttpServletRequest request) {
        return ProblemasHttp.respuesta(ProblemasHttp.crear(503, "SERVICIO_NO_DISPONIBLE",
            "El servicio no está disponible temporalmente. Inténtalo más tarde.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(HttpServletRequest request) {
        return ProblemasHttp.respuesta(ProblemasHttp.crear(500, "ERROR_INTERNO",
            "No se pudo completar la operación.", request.getRequestURI()));
    }

    private static String mensaje(String campo, String restriccion) {
        if ("nombre".equals(campo)) return "Size".equals(restriccion)
            ? "El nombre debe tener como máximo 100 caracteres." : "El nombre es obligatorio.";
        if ("avatarId".equals(campo)) return "El avatar no es válido.";
        if ("comprobanteRegistro".equals(campo)) return "La verificación de registro no es válida.";
        if ("refreshToken".equals(campo)) return "La credencial de sesión no es válida.";
        if ("NotBlank".equals(restriccion)) return "email".equals(campo)
            ? "El correo es obligatorio." : "El código es obligatorio.";
        if ("Size".equals(restriccion)) return "El correo debe tener como máximo 150 caracteres.";
        return "codigo".equals(campo) ? "Debe contener exactamente 6 dígitos." : "El correo debe ser válido.";
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static ResponseEntity<Object> objeto(ResponseEntity<ProblemDetail> response) {
        return new ResponseEntity<>(response.getBody(), response.getHeaders(), response.getStatusCode());
    }
}
