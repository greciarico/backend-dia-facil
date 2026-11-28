package dia_facil.backend.web.perfiles;

import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.auth.SesionInvalidada;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import dia_facil.backend.service.fotos.FotosPerfilService;
import dia_facil.backend.service.fotos.CloudinaryFotos.Destino;
import dia_facil.backend.service.perfiles.*;
import dia_facil.backend.web.perfiles.dto.CrearPerfilRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FotosPerfilController {
    private final FotosPerfilService fotos;
    private final CrearPerfilService perfiles;
    private final PerfilUsuarioRepository personas;
    private final CuidadorRepository cuidadores;
    private final ObjectProvider<AccesoDispositivoService> acceso;

    public FotosPerfilController(FotosPerfilService fotos, CrearPerfilService perfiles, PerfilUsuarioRepository personas,
        CuidadorRepository cuidadores, ObjectProvider<AccesoDispositivoService> acceso) {
        this.fotos = fotos;
        this.perfiles = perfiles;
        this.personas = personas;
        this.cuidadores = cuidadores;
        this.acceso = acceso;
    }

    @PostMapping(path = "/api/perfiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PerfilResumen> crear(@AuthenticationPrincipal Identidad identidad,
        @ModelAttribute CrearPerfilRequest datos, @RequestPart("foto") java.util.List<MultipartFile> archivos) {
        int id = propietario(identidad);
        if (datos.avatar() != null || archivos.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        perfiles.validarFoto(id, datos.nombre());
        var perfil = fotos.crear(Destino.PERSONAS, archivos.getFirst(), ref -> perfiles.crear(id, datos.nombre(), null, ref));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(perfil);
    }

    @GetMapping("/api/auth/cuidadores/foto")
    public ResponseEntity<byte[]> cuidador(@AuthenticationPrincipal Identidad identidad) {
        var referencia = cuidadores.findById(propietario(identidad)).filter(c -> c.getEstado() == EstadoCuidador.ACTIVO)
            .map(c -> c.getFotoReferencia()).orElse(null);
        return jpeg(fotos.leer(Destino.CUIDADORES, referencia));
    }

    @GetMapping("/api/perfiles/{id}/foto")
    public ResponseEntity<byte[]> persona(@AuthenticationPrincipal Identidad identidad, @PathVariable Integer id) {
        var referencia = personas.findByIdPerfilUsuarioAndIdCuidadorAndEstado(id, propietario(identidad), EstadoPerfil.ACTIVO)
            .map(p -> p.getFotoReferencia()).orElse(null);
        return jpeg(fotos.leer(Destino.PERSONAS, referencia));
    }

    @GetMapping("/api/usuario/foto")
    public ResponseEntity<byte[]> usuario(@AuthenticationPrincipal AccesoDispositivoService.Identidad identidad) {
        var perfil = acceso.getObject().perfilActivo(identidad);
        var referencia = personas.findByIdPerfilUsuarioAndEstado(perfil.idPerfilUsuario(), EstadoPerfil.ACTIVO)
            .map(p -> p.getFotoReferencia()).orElse(null);
        return jpeg(fotos.leer(Destino.PERSONAS, referencia));
    }

    private static int propietario(Identidad identidad) {
        if (identidad == null) throw new SesionInvalidada(false);
        return identidad.cuidador().idCuidador();
    }

    private static ResponseEntity<byte[]> jpeg(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore().cachePrivate()).body(bytes);
    }
}
