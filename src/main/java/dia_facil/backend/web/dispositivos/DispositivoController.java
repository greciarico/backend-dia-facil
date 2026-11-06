package dia_facil.backend.web.dispositivos;

import dia_facil.backend.service.dispositivos.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/dispositivos")
@ConditionalOnProperty(name = "diafacil.dispositivos.enabled", havingValue = "true")
public class DispositivoController {
    private final AccesoDispositivoService service;
    public DispositivoController(AccesoDispositivoService service) { this.service = service; }
    public record VincularRequest(String codigoVinculacion, String nombreDispositivo, java.util.List<String> credencialesPrevias) {
        @Override public String toString() { return "VincularRequest[contenido reservado]"; }
    }
    @PostMapping("/preparar-vinculacion")
    public ResponseEntity<AccesoDispositivoService.Preparacion> preparar() {
        for (int i = 0; i < 3; i++) {
            try { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.preparar()); }
            catch (org.springframework.dao.DataIntegrityViolationException ex) {
                boolean collision = false;
                for (Throwable c = ex; c != null; c = c.getCause()) if (c instanceof org.hibernate.exception.ConstraintViolationException v
                    && v.getConstraintName() != null && v.getConstraintName().replace("`", "").endsWith("uq_dispositivo_token")) collision = true;
                if (!collision) throw ex;
            }
        }
        throw new RechazoDispositivo(503,"SERVICIO_NO_DISPONIBLE","Inténtalo más tarde.");
    }
    @PostMapping(path = "/vincular", consumes = "application/json")
    public ResponseEntity<AccesoDispositivoService.Vinculacion> vincular(@AuthenticationPrincipal AccesoDispositivoService.Identidad principal,
        @RequestBody VincularRequest request) {
        if (principal == null) throw RechazoDispositivo.invalida();
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(service.vincular(principal.idCredencial(), request.codigoVinculacion(), request.nombreDispositivo(), request.credencialesPrevias()));
    }
    @GetMapping("/vinculacion")
    public ResponseEntity<AccesoDispositivoService.Estado> actual(@AuthenticationPrincipal AccesoDispositivoService.Identidad principal) {
        if (principal == null) throw RechazoDispositivo.invalida();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(principal.idCredencial()));
    }
    @DeleteMapping("/vinculacion")
    public ResponseEntity<Void> salir(@AuthenticationPrincipal AccesoDispositivoService.Identidad principal) {
        if (principal == null) throw RechazoDispositivo.invalida();
        service.salir(principal.idCredencial()); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
