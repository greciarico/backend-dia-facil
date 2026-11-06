package dia_facil.backend.web.perfiles;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.PerfilUsuarioService;
import dia_facil.backend.service.perfiles.CrearPerfilService;
import dia_facil.backend.service.perfiles.PerfilResumen;
import dia_facil.backend.web.perfiles.dto.CrearPerfilRequest;
import dia_facil.backend.service.perfiles.PoliticaPerfiles;
import dia_facil.backend.web.perfiles.dto.PerfilesResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/perfiles")
public class PerfilUsuarioController {
    private final PerfilUsuarioService perfiles;
    private final CrearPerfilService creacion;
    public PerfilUsuarioController(PerfilUsuarioService perfiles, CrearPerfilService creacion) { this.perfiles = perfiles; this.creacion = creacion; }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<PerfilResumen> crear(@AuthenticationPrincipal Identidad identidad, @RequestBody CrearPerfilRequest request) {
        if (identidad == null) throw new SesionInvalidada(false);
        var perfil = creacion.crear(identidad.cuidador().idCuidador(), request.nombre(), request.avatar());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(perfil);
    }

    @GetMapping
    public ResponseEntity<PerfilesResponse> consultar(@AuthenticationPrincipal Identidad identidad) {
        if (identidad == null) throw new SesionInvalidada(false);
        var response = new PerfilesResponse(perfiles.consultar(identidad.cuidador().idCuidador()), PoliticaPerfiles.MAXIMO_ACTIVOS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/{idPerfilUsuario}")
    public ResponseEntity<PerfilResumen> detalle(@AuthenticationPrincipal Identidad identidad, @PathVariable("idPerfilUsuario") Integer idPerfilUsuario) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(perfiles.detalle(identidad.cuidador().idCuidador(), idPerfilUsuario));
    }
}
