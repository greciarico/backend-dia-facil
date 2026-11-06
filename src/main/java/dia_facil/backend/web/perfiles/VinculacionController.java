package dia_facil.backend.web.perfiles;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.VinculacionService;
import dia_facil.backend.service.perfiles.VinculacionResumen;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfiles/{idPerfilUsuario}/vinculacion")
public class VinculacionController {
    private final VinculacionService vinculacion;
    public VinculacionController(VinculacionService vinculacion) { this.vinculacion = vinculacion; }
    public record RenovarRequest(String codigoActual) {}
    @GetMapping
    public ResponseEntity<VinculacionResumen> consultar(@AuthenticationPrincipal Identidad identidad,
        @PathVariable("idPerfilUsuario") Integer id) {
        if (identidad == null) throw new SesionInvalidada(false);
        return respuesta(vinculacion.consultar(identidad.cuidador().idCuidador(), id));
    }
    @PostMapping(path = "/renovar-codigo", consumes = "application/json")
    public ResponseEntity<VinculacionResumen> renovar(@AuthenticationPrincipal Identidad identidad,
        @PathVariable("idPerfilUsuario") Integer id, @RequestBody RenovarRequest request) {
        if (identidad == null) throw new SesionInvalidada(false);
        return respuesta(vinculacion.renovar(identidad.cuidador().idCuidador(), id, request.codigoActual()));
    }
    private ResponseEntity<VinculacionResumen> respuesta(VinculacionResumen resumen) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resumen);
    }
}
