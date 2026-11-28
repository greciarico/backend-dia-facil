package dia_facil.backend.web.auth;

import dia_facil.backend.service.auth.AutenticacionCuidadorService;
import dia_facil.backend.service.fotos.FotosPerfilService;
import dia_facil.backend.service.fotos.CloudinaryFotos.Destino;
import dia_facil.backend.web.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnProperty(name = "diafacil.auth.enabled", havingValue = "true")
public class RegistroFotoController {
    private final AutenticacionCuidadorService auth;
    private final FotosPerfilService fotos;
    public RegistroFotoController(AutenticacionCuidadorService auth, FotosPerfilService fotos) {
        this.auth = auth;
        this.fotos = fotos;
    }

    @PostMapping(path = "/api/auth/cuidadores/completar-registro", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CompletarRegistroResponse> registrar(@Valid @ModelAttribute CompletarRegistroRequest datos,
        @RequestPart("foto") java.util.List<MultipartFile> fotosRecibidas) {
        if (datos.avatarId() != null || fotosRecibidas.size() != 1) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        auth.validarRegistroFoto(datos.comprobanteRegistro(), datos.nombre());
        var grant = fotos.crear(Destino.CUIDADORES, fotosRecibidas.getFirst(), id -> auth.registrarFoto(datos.comprobanteRegistro(), datos.nombre(), id));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(new CompletarRegistroResponse("REGISTRO_COMPLETADO", grant.cuidador().idCuidador(), grant.cuidador(), grant.sesion()));
    }
}
