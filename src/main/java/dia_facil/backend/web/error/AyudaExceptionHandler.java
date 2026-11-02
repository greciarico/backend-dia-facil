package dia_facil.backend.web.error;
import dia_facil.backend.service.ayuda.RechazoAyuda;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AyudaExceptionHandler {
    @ExceptionHandler(RechazoAyuda.class)
    public ResponseEntity<ProblemDetail> rechazo(RechazoAyuda ex,HttpServletRequest request){
        var body=ProblemasHttp.crear(ex.status(),ex.codigo(),ex.getMessage(),request.getRequestURI());
        var builder=ResponseEntity.status(ex.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore());
        if(ex.espera()>0)builder.header(HttpHeaders.RETRY_AFTER,Long.toString(ex.espera()));return builder.body(body);
    }
}
