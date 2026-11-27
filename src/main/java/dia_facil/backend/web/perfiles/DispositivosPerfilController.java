package dia_facil.backend.web.perfiles;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.VinculacionTransaccionalService;
import dia_facil.backend.service.perfiles.VinculacionTransaccionalService.DispositivoResumen;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfiles/{perfil}/dispositivos")
public class DispositivosPerfilController {
    private final VinculacionTransaccionalService vinculacion;

    public DispositivosPerfilController(VinculacionTransaccionalService vinculacion) {
        this.vinculacion = vinculacion;
    }

    public record Dispositivos(List<DispositivoResumen> dispositivos) {}

    @GetMapping
    public ResponseEntity<Dispositivos> listar(@AuthenticationPrincipal Identidad identidad, @PathVariable Integer perfil) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Dispositivos(vinculacion.listarDispositivos(identidad.cuidador().idCuidador(), perfil)));
    }

    @DeleteMapping("/{dispositivo}")
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal Identidad identidad, @PathVariable Integer perfil,
        @PathVariable Integer dispositivo) {
        if (identidad == null) throw new SesionInvalidada(false);
        vinculacion.desvincular(identidad.cuidador().idCuidador(), perfil, dispositivo);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
