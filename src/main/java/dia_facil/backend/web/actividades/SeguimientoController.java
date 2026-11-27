package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.SeguimientoService;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfiles/{perfil}/seguimiento")
public class SeguimientoController {
    private final SeguimientoService seguimiento;
    public SeguimientoController(SeguimientoService seguimiento) { this.seguimiento = seguimiento; }

    @GetMapping
    public ResponseEntity<SeguimientoService.Dia> consultar(@AuthenticationPrincipal Identidad identidad,
            @PathVariable("perfil") Integer perfil, @RequestParam(name="fecha", required=false) String fecha) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(seguimiento.consultar(identidad.cuidador().idCuidador(), perfil, fecha));
    }

    @GetMapping("/{actividad}")
    public ResponseEntity<SeguimientoService.Detalle> detalle(@AuthenticationPrincipal Identidad identidad,
            @PathVariable("perfil") Integer perfil, @PathVariable("actividad") Integer actividad, @RequestParam("fecha") String fecha) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(seguimiento.detalle(identidad.cuidador().idCuidador(), perfil, actividad, fecha));
    }
}
