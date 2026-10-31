package dia_facil.backend.web.cuidadores;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.cuidadores.AccesibilidadCuidadorService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cuidador/accesibilidad")
public class AccesibilidadCuidadorController {
    private final AccesibilidadCuidadorService service;
    public AccesibilidadCuidadorController(AccesibilidadCuidadorService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<AccesibilidadCuidadorService.Preferencias> consultar(@AuthenticationPrincipal Identidad principal) { return ok(service.consultar(id(principal))); }
    @PatchMapping(consumes = "application/json")
    public ResponseEntity<AccesibilidadCuidadorService.Preferencias> actualizar(@AuthenticationPrincipal Identidad principal, @RequestBody Map<String, Object> cambios) { return ok(service.actualizar(id(principal), cambios)); }
    @PostMapping("/restablecer")
    public ResponseEntity<AccesibilidadCuidadorService.Preferencias> restablecer(@AuthenticationPrincipal Identidad principal) { return ok(service.restablecer(id(principal))); }
    private Integer id(Identidad principal) { if (principal == null) throw new SesionInvalidada(false); return principal.cuidador().idCuidador(); }
    private ResponseEntity<AccesibilidadCuidadorService.Preferencias> ok(AccesibilidadCuidadorService.Preferencias value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
