package dia_facil.backend.web.actividades;

import dia_facil.backend.service.actividades.*;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService.Identidad;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuario/actividades/{actividad}")
@ConditionalOnProperty(name="diafacil.dispositivos.enabled",havingValue="true")
public class EjecucionUsuarioController {
    private final EjecucionUsuarioService service;
    public EjecucionUsuarioController(EjecucionUsuarioService service){this.service=service;}
    public record FechaRequest(LocalDate fecha){}
    public record PasoRequest(Integer idPasoActual){}
    @GetMapping("/guia")
    public ResponseEntity<EjecucionUsuarioService.Guia> consultar(@AuthenticationPrincipal Identidad principal,@PathVariable("actividad") Integer actividad){return ok(service.consultar(principal,actividad,null));}
    @GetMapping("/ejecuciones/{ejecucion}")
    public ResponseEntity<EjecucionUsuarioService.Guia> recuperar(@AuthenticationPrincipal Identidad principal,@PathVariable("actividad") Integer actividad,@PathVariable("ejecucion") Integer ejecucion){return ok(service.consultar(principal,actividad,ejecucion));}
    @PostMapping("/guia")
    public ResponseEntity<EjecucionUsuarioService.Guia> iniciar(@AuthenticationPrincipal Identidad principal,@PathVariable("actividad") Integer actividad,@RequestBody FechaRequest body){return ok(service.iniciar(principal,actividad,fecha(body)));}
    @PutMapping("/ejecuciones/{ejecucion}/pasos/{paso}/completar")
    public ResponseEntity<EjecucionUsuarioService.Guia> avanzar(@AuthenticationPrincipal Identidad principal,@PathVariable("actividad") Integer actividad,@PathVariable("ejecucion") Integer ejecucion,@PathVariable("paso") Integer paso){return ok(service.avanzar(principal,actividad,ejecucion,paso));}
    @PostMapping("/ejecuciones/{ejecucion}/finalizar-guiada")
    public ResponseEntity<EjecucionUsuarioService.Finalizacion> finalizar(@AuthenticationPrincipal Identidad principal,@PathVariable("actividad") Integer actividad,@PathVariable("ejecucion") Integer ejecucion,@RequestBody PasoRequest body){
        if(body.idPasoActual()==null)throw RechazoActividad.datos("idPasoActual","Selecciona el paso actual.");
        return ok(service.finalizarGuiada(principal,actividad,ejecucion,body.idPasoActual()));
    }
    @PostMapping("/finalizar-directa")
    public ResponseEntity<EjecucionUsuarioService.Finalizacion> directa(@AuthenticationPrincipal Identidad principal,@PathVariable("actividad") Integer actividad,@RequestBody FechaRequest body){return ok(service.finalizarDirecta(principal,actividad,fecha(body)));}
    private LocalDate fecha(FechaRequest body){if(body.fecha()==null)throw RechazoActividad.datos("fecha","Consulta nuevamente la actividad.");return body.fecha();}
    private <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
