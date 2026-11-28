package dia_facil.backend.web.auth;

import dia_facil.backend.service.auth.DatosPersonalesService;
import dia_facil.backend.service.auth.DatosPersonalesService.Datos;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.EditarPerfilService.Entrada;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/cuidadores/datos-personales")
public class DatosPersonalesController {
    private final DatosPersonalesService servicio;
    public DatosPersonalesController(DatosPersonalesService servicio) { this.servicio = servicio; }

    @GetMapping
    public ResponseEntity<Datos> consultar(@AuthenticationPrincipal Identidad identidad) {
        return respuesta(servicio.consultar(id(identidad)));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Datos> editar(@AuthenticationPrincipal Identidad identidad, @RequestBody Entrada datos) {
        return respuesta(servicio.editar(id(identidad), datos, null));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Datos> editarFoto(@AuthenticationPrincipal Identidad identidad,
        @ModelAttribute Entrada datos, @RequestPart("foto") List<MultipartFile> archivos) {
        if (archivos.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return respuesta(servicio.editar(id(identidad), datos, archivos.getFirst()));
    }

    private static int id(Identidad identidad) {
        if (identidad == null) throw new SesionInvalidada(false);
        return identidad.cuidador().idCuidador();
    }

    private static ResponseEntity<Datos> respuesta(Datos datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(datos);
    }
}
