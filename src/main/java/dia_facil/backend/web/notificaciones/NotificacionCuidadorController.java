package dia_facil.backend.web.notificaciones;
import dia_facil.backend.service.notificaciones.NotificacionCuidadorService;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionCuidadorController {
    private final NotificacionCuidadorService service;
    public NotificacionCuidadorController(NotificacionCuidadorService service){this.service=service;}
    @GetMapping public ResponseEntity<NotificacionCuidadorService.Listado> listar(@AuthenticationPrincipal Identidad principal,@RequestParam(name="categoria",defaultValue="TODAS") String categoria,@RequestParam(name="cursor",required=false) String cursor){return ok(service.listar(principal,categoria,cursor));}
    @GetMapping("/{id}") public ResponseEntity<NotificacionCuidadorService.Notificacion> consultar(@AuthenticationPrincipal Identidad principal,@PathVariable("id") Integer id){return ok(service.consultar(principal,id));}
    @PutMapping("/{id}/leida") public ResponseEntity<NotificacionCuidadorService.Notificacion> leer(@AuthenticationPrincipal Identidad principal,@PathVariable("id") Integer id){return ok(service.leer(principal,id));}
    private <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
