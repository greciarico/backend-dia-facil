package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.EdicionActividadService;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfiles/{perfil}/actividades/{actividad}")
public class EdicionActividadController {
    private final EdicionActividadService service;
    public EdicionActividadController(EdicionActividadService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<EdicionActividadService.Detalle> consultar(@AuthenticationPrincipal Identidad identidad,
            @PathVariable("perfil") Integer perfil, @PathVariable("actividad") Integer actividad) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(identidad.cuidador().idCuidador(), perfil, actividad));
    }

    @DeleteMapping
    public ResponseEntity<EdicionActividadService.Eliminacion> eliminar(@AuthenticationPrincipal Identidad identidad,
            @PathVariable("perfil") Integer perfil, @PathVariable("actividad") Integer actividad,
            @RequestParam("revision") int revision) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.eliminar(identidad.cuidador().idCuidador(), perfil, actividad, revision));
    }

    @PutMapping
    public ResponseEntity<EdicionActividadService.Detalle> editar(@AuthenticationPrincipal Identidad identidad,
            @PathVariable("perfil") Integer perfil, @PathVariable("actividad") Integer actividad,
            @RequestBody EdicionActividadService.Entrada entrada) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.editar(identidad.cuidador().idCuidador(), perfil, actividad, entrada));
    }
}
