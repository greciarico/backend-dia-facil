package dia_facil.backend.web.ayuda;
import dia_facil.backend.service.ayuda.SolicitudAyudaService;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService.Identidad;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/usuario/solicitudes-ayuda")
@ConditionalOnProperty(name="diafacil.dispositivos.enabled",havingValue="true")
public class SolicitudAyudaController {
    private final SolicitudAyudaService service;
    public SolicitudAyudaController(SolicitudAyudaService service){this.service=service;}
    @PostMapping public ResponseEntity<SolicitudAyudaService.Ayuda> crear(@AuthenticationPrincipal Identidad principal,@RequestBody SolicitudAyudaService.Peticion body){return ok(service.crear(principal,body));}
    @GetMapping("/por-clave/{clave}") public ResponseEntity<SolicitudAyudaService.Ayuda> consultar(@AuthenticationPrincipal Identidad principal,@PathVariable("clave") String clave){return ok(service.consultar(principal,clave));}
    private <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
