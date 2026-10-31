package dia_facil.backend.web.dispositivos;

import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import dia_facil.backend.service.perfiles.AccesibilidadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "diafacil.dispositivos.enabled", havingValue = "true")
public class AccesibilidadUsuarioController {
    private final AccesoDispositivoService acceso;
    private final AccesibilidadService service;
    public AccesibilidadUsuarioController(AccesoDispositivoService acceso, AccesibilidadService service) { this.acceso = acceso; this.service = service; }
    @GetMapping("/api/usuario/accesibilidad")
    public ResponseEntity<AccesibilidadService.Preferencias> consultar(@AuthenticationPrincipal AccesoDispositivoService.Identidad principal) {
        var perfil = acceso.perfilActivo(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultarUsuario(perfil.idPerfilUsuario()));
    }
}
