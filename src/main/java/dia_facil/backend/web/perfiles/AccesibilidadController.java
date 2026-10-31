package dia_facil.backend.web.perfiles;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.AccesibilidadService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfiles/{perfil}/accesibilidad")
public class AccesibilidadController {
    private final AccesibilidadService service;
    public AccesibilidadController(AccesibilidadService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<AccesibilidadService.Preferencias> consultar(@AuthenticationPrincipal Identidad principal, @PathVariable("perfil") Integer perfil) {
        return ok(service.consultar(cuidador(principal), perfil));
    }
    @PatchMapping(consumes = "application/json")
    public ResponseEntity<AccesibilidadService.Preferencias> actualizar(@AuthenticationPrincipal Identidad principal, @PathVariable("perfil") Integer perfil, @RequestBody Map<String, Object> cambios) {
        return ok(service.actualizar(cuidador(principal), perfil, cambios));
    }
    @PostMapping("/restablecer")
    public ResponseEntity<AccesibilidadService.Preferencias> restablecer(@AuthenticationPrincipal Identidad principal, @PathVariable("perfil") Integer perfil) {
        return ok(service.restablecer(cuidador(principal), perfil));
    }
    private Integer cuidador(Identidad principal) { if (principal == null) throw new SesionInvalidada(false); return principal.cuidador().idCuidador(); }
    private ResponseEntity<AccesibilidadService.Preferencias> ok(AccesibilidadService.Preferencias p) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(p); }
}
