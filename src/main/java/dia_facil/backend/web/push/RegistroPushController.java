package dia_facil.backend.web.push;

import dia_facil.backend.service.push.RegistroPushService;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/push/registro")
public class RegistroPushController {
    private final RegistroPushService service;
    public RegistroPushController(RegistroPushService service) { this.service = service; }

    @PutMapping
    public ResponseEntity<RegistroPushService.Estado> put(@AuthenticationPrincipal Identidad p,
            @RequestBody RegistroPushService.Peticion b,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expected) {
        var value = reintentar(() -> service.registrar(p, b, expected));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(value.etag()).body(value.estado());
    }
    @GetMapping
    public ResponseEntity<RegistroPushService.Estado> get(@AuthenticationPrincipal Identidad p,
            @RequestParam("idInstalacion") UUID id) {
        var value = service.consultar(p, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(value.etag()).body(value.estado());
    }
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Identidad p,
            @RequestParam("idInstalacion") UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expected) {
        var tag = reintentar(() -> service.eliminar(p, id, expected));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).eTag(tag).build();
    }
    private <T> T reintentar(Supplier<T> operation) {
        for (int i = 0; i < 3; i++) {
            try { return operation.get(); }
            catch (org.springframework.dao.DataIntegrityViolationException | org.springframework.dao.PessimisticLockingFailureException ex) {
                if (i == 2) throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.CONFLICT, "Reintenta el registro de esta instalación.");
            }
        }
        throw new IllegalStateException();
    }
}
