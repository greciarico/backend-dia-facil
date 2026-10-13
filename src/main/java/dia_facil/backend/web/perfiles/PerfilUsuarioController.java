package dia_facil.backend.web.perfiles;

import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.perfiles.PerfilUsuarioService;
import dia_facil.backend.service.perfiles.PoliticaPerfiles;
import dia_facil.backend.web.perfiles.dto.PerfilesResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfiles")
public class PerfilUsuarioController {
    private final PerfilUsuarioService perfiles;
    public PerfilUsuarioController(PerfilUsuarioService perfiles) { this.perfiles = perfiles; }

    @GetMapping
    public ResponseEntity<PerfilesResponse> consultar(@AuthenticationPrincipal Identidad identidad) {
        if (identidad == null) throw new SesionInvalidada(false);
        var response = new PerfilesResponse(perfiles.consultar(identidad.cuidador().idCuidador()), PoliticaPerfiles.MAXIMO_ACTIVOS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
