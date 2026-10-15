package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.ActividadService;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfiles/{idPerfilUsuario}/actividades")
public class ActividadController {
    private final ActividadService actividades;

    public ActividadController(ActividadService actividades) { this.actividades = actividades; }

    @GetMapping
    public ResponseEntity<ActividadesResponse> consultar(@AuthenticationPrincipal Identidad identidad,
        @PathVariable("idPerfilUsuario") Integer idPerfilUsuario) {
        if (identidad == null) throw new SesionInvalidada(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new ActividadesResponse(actividades.consultar(identidad.cuidador().idCuidador(), idPerfilUsuario)));
    }
}
