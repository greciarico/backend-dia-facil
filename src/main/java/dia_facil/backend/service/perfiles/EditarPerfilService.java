package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.PerfilUsuarioRepository;
import dia_facil.backend.service.avatares.CatalogoAvatares;
import dia_facil.backend.service.fotos.CloudinaryFotos.Destino;
import dia_facil.backend.service.fotos.FotosPerfilService;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EditarPerfilService {
    public enum Imagen { CONSERVAR, AVATAR, INICIAL, FOTO }
    public record Entrada(Integer revision, String nombre, Imagen imagen, String avatar) {}

    private final PerfilUsuarioRepository perfiles;
    private final FotosPerfilService fotos;
    private final EntityManager em;
    private final TransactionTemplate transaccion;
    private final String entorno;

    public EditarPerfilService(PerfilUsuarioRepository perfiles, FotosPerfilService fotos, EntityManager em,
        PlatformTransactionManager tx, @Value("${diafacil.cloudinary.entorno:dev}") String entorno) {
        this.perfiles = perfiles;
        this.fotos = fotos;
        this.em = em;
        this.transaccion = new TransactionTemplate(tx);
        this.entorno = entorno;
    }

    public PerfilResumen editar(int cuidador, int id, Entrada datos, MultipartFile foto) {
        String nombre = datos.nombre() == null ? "" : datos.nombre().strip();
        if (nombre.isBlank() || nombre.length() > 60) throw invalido("nombre");
        if (datos.revision() == null || datos.revision() < 1) throw invalido("revision");
        var imagen = datos.imagen();
        if (imagen == null || (imagen == Imagen.FOTO) != (foto != null)
            || (imagen == Imagen.AVATAR ? datos.avatar() == null || !CatalogoAvatares.ids().contains(datos.avatar()) : datos.avatar() != null))
            throw invalido("imagen");

        if (foto != null) {
            comprobarRevision(perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(id, cuidador, EstadoPerfil.ACTIVO)
                .orElseThrow(RechazoPerfil::noEncontrado), datos.revision());
            return fotos.crear(Destino.PERSONAS, foto,
                referencia -> guardar(cuidador, id, datos, nombre, referencia));
        }
        return guardar(cuidador, id, datos, nombre, null);
    }

    private PerfilResumen guardar(int cuidador, int id, Entrada datos, String nombre, String referencia) {
        return transaccion.execute(status -> {
            var perfil = perfiles.bloquear(id, cuidador, EstadoPerfil.ACTIVO).orElseThrow(RechazoPerfil::noEncontrado);
            em.refresh(perfil, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (perfil.getEstado() != EstadoPerfil.ACTIVO) throw RechazoPerfil.noEncontrado();
            comprobarRevision(perfil, datos.revision());

            String avatar = datos.imagen() == Imagen.CONSERVAR ? perfil.getAvatar() : datos.avatar();
            String anterior = perfil.getFotoReferencia();
            String siguiente = datos.imagen() == Imagen.CONSERVAR ? anterior : referencia;
            if (!Objects.equals(nombre, perfil.getNombre()) || !Objects.equals(avatar, perfil.getAvatar())
                || !Objects.equals(anterior, siguiente)) {
                FotosPerfilService.confirmar(em, referencia);
                perfil.setNombre(nombre);
                perfil.setAvatar(avatar);
                perfil.setFotoReferencia(siguiente);
                perfil.setRevisionPerfil(perfil.getRevisionPerfil() + 1);
                FotosPerfilService.programarRetirada(em, anterior, siguiente, entorno, Destino.PERSONAS);
            }
            return new PerfilResumen(id, perfil.getNombre(), perfil.getAvatar(),
                perfil.getFotoReferencia() == null ? null : true, perfil.getRevisionPerfil());
        });
    }

    private static void comprobarRevision(PerfilUsuario perfil, int esperada) {
        if (perfil.getRevisionPerfil() != esperada)
            throw new RechazoPerfil("PERFIL_MODIFICADO", "El perfil cambió. Consulta su versión actual.", null);
    }

    private static RechazoPerfil invalido(String campo) {
        return new RechazoPerfil("SOLICITUD_INVALIDA", "Revisa los datos del perfil.", campo);
    }
}
