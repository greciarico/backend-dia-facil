package dia_facil.backend.web.auth;

import dia_facil.backend.service.auth.*;
import dia_facil.backend.web.auth.dto.RefreshRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth/cuidadores")
@ConditionalOnProperty(prefix = "diafacil.auth", name = "enabled", havingValue = "true")
public class SesionCuidadorController {
    private final SesionCuidadorService sesiones;
    public SesionCuidadorController(SesionCuidadorService sesiones) { this.sesiones = sesiones; }
    @PostMapping(value = "/renovar-sesion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SesionCuidadorService.Concesion> renovar(@Valid @RequestBody RefreshRequest request) {
        var grant = sesiones.renovar(request.refreshToken());
        if (grant == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(grant);
    }
    @PostMapping(value = "/cerrar-sesion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cerrar(@Valid @RequestBody RefreshRequest request) {
        sesiones.cerrar(request.refreshToken()); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/sesion")
    public ResponseEntity<SesionCuidadorService.CuidadorResumen> actual(@AuthenticationPrincipal SesionCuidadorService.Identidad identidad) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identidad.cuidador());
    }
}
