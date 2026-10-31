package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.PictogramaArasaac;
import dia_facil.backend.service.actividades.PictogramaArasaacService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pictogramas")
public class PictogramaArasaacController {
    public record Respuesta(List<PictogramaArasaac> pictogramas) {}
    private final PictogramaArasaacService service;
    public PictogramaArasaacController(PictogramaArasaacService service) { this.service = service; }
    @GetMapping("/arasaac")
    public ResponseEntity<Respuesta> buscar(@RequestParam(name = "texto", required = false) String texto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(service.buscar(texto)));
    }
}
