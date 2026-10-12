package dia_facil.backend.web.auth;

import dia_facil.backend.service.auth.AutenticacionCuidadorService;
import dia_facil.backend.web.auth.dto.SolicitarCodigoRequest;
import dia_facil.backend.web.auth.dto.SolicitarCodigoResponse;
import dia_facil.backend.web.auth.dto.VerificarCodigoRequest;
import dia_facil.backend.web.auth.dto.VerificarCodigoResponse;
import dia_facil.backend.web.error.ProblemasHttp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/cuidadores")
@ConditionalOnProperty(prefix = "diafacil.auth", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AutenticacionCuidadorController {
    private final AutenticacionCuidadorService service;

    public AutenticacionCuidadorController(AutenticacionCuidadorService service) {
        this.service = service;
    }

    @PostMapping(value = "/solicitar-codigo", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SolicitarCodigoResponse> solicitar(@Valid @RequestBody SolicitarCodigoRequest request) {
        service.solicitarCodigo(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SolicitarCodigoResponse(
            "CODIGO_SOLICITADO", "Revisa tu correo para encontrar el código.", 60));
    }

    @PostMapping(value = "/verificar-codigo", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> verificar(@Valid @RequestBody VerificarCodigoRequest request, HttpServletRequest http) {
        var result = service.verificarCodigo(request.email(), request.codigo());
        return switch (result.estado()) {
            case AUTENTICADO -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new VerificarCodigoResponse("AUTENTICADO", result.idCuidador(), null));
            case REGISTRO_PENDIENTE -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new VerificarCodigoResponse("REGISTRO_PENDIENTE", null, result.comprobante()));
            case RECHAZADO -> ProblemasHttp.rechazo(result.motivo(), http.getRequestURI());
        };
    }
}
