package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.AgendaUsuarioService;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService.Identidad;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="diafacil.dispositivos.enabled",havingValue="true")
public class AgendaUsuarioController {
    private final AgendaUsuarioService agenda;
    public AgendaUsuarioController(AgendaUsuarioService agenda){this.agenda=agenda;}
    @GetMapping("/api/usuario/agenda")
    public ResponseEntity<AgendaUsuarioService.Agenda> consultar(@AuthenticationPrincipal Identidad principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agenda.consultar(principal));
    }
    @GetMapping("/api/usuario/actividades/{idActividad}")
    public ResponseEntity<AgendaUsuarioService.Detalle> detalle(@AuthenticationPrincipal Identidad principal,
        @PathVariable("idActividad") Integer idActividad) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agenda.detalle(principal,idActividad));
    }
}
