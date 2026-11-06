package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.IconoActividadService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iconos-actividad")
public class IconoActividadController {
    public record Respuesta(List<IconoActividadService.Icono> iconos) {}
    private final IconoActividadService service;
    public IconoActividadController(IconoActividadService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Respuesta> consultar(@RequestParam(name = "nombre", required = false) String nombre) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(service.consultar(nombre)));
    }
}
