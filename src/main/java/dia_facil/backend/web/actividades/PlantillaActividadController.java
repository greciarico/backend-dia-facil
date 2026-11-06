package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.PlantillaActividadService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plantillas-actividad")
public class PlantillaActividadController {
    public record Respuesta(PlantillaActividadService.Plantilla plantilla) {}
    private final PlantillaActividadService service;
    public PlantillaActividadController(PlantillaActividadService service) { this.service = service; }
    @GetMapping("/sugerencia")
    public ResponseEntity<Respuesta> sugerir(@RequestParam(name = "nombre", required = false) String nombre) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(service.sugerir(nombre)));
    }
}
