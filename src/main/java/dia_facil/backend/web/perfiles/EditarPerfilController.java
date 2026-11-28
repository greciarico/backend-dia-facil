package dia_facil.backend.web.perfiles;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.EditarPerfilService;
import dia_facil.backend.service.perfiles.EditarPerfilService.Entrada;
import dia_facil.backend.service.perfiles.PerfilResumen;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/perfiles/{id}")
public class EditarPerfilController {
    private final EditarPerfilService servicio;
    public EditarPerfilController(EditarPerfilService servicio) { this.servicio = servicio; }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PerfilResumen> editar(@AuthenticationPrincipal Identidad identidad,
        @PathVariable int id, @RequestBody Entrada datos) {
        return editar(identidad, id, datos, null);
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PerfilResumen> editarFoto(@AuthenticationPrincipal Identidad identidad,
        @PathVariable int id, @ModelAttribute Entrada datos, @RequestPart("foto") List<MultipartFile> archivos) {
        if (archivos.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return editar(identidad, id, datos, archivos.getFirst());
    }

    private ResponseEntity<PerfilResumen> editar(Identidad identidad, int id, Entrada datos, MultipartFile foto) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(servicio.editar(identidad.cuidador().idCuidador(), id, datos, foto));
    }
}
