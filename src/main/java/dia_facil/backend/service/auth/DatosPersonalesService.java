package dia_facil.backend.service.auth;

import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.enums.EstadoCuidador;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import dia_facil.backend.service.fotos.CloudinaryFotos.Destino;
import dia_facil.backend.service.fotos.FotosPerfilService;
import dia_facil.backend.service.perfiles.EditarPerfilService.Entrada;
import dia_facil.backend.service.perfiles.EditarPerfilService.Imagen;
import dia_facil.backend.service.perfiles.RechazoPerfil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DatosPersonalesService {
    public record Datos(String correo, SesionCuidadorService.CuidadorResumen cuidador) {
        @Override public String toString() { return "Datos[contenido reservado]"; }
    }

    private final CuidadorRepository cuidadores;
    private final FotosPerfilService fotos;
    private final EntityManager em;
    private final TransactionTemplate transaccion;
    private final String entorno;

    public DatosPersonalesService(CuidadorRepository cuidadores, FotosPerfilService fotos, EntityManager em,
        PlatformTransactionManager tx, @Value("${diafacil.cloudinary.entorno:dev}") String entorno) {
        this.cuidadores = cuidadores;
        this.fotos = fotos;
        this.em = em;
        this.transaccion = new TransactionTemplate(tx);
        this.entorno = entorno;
    }

    public Datos consultar(int id) {
        return datos(activo(cuidadores.findById(id).orElseThrow(RechazoPerfil::noEncontrado)));
    }

    public Datos editar(int id, Entrada datos, MultipartFile foto) {
        String nombre = datos.nombre() == null ? "" : datos.nombre().strip();
        if (nombre.isBlank() || nombre.length() > 100) throw invalido("nombre");
        if (datos.revision() == null || datos.revision() < 1) throw invalido("revision");
        var imagen = datos.imagen();
        if (imagen == null || (imagen == Imagen.FOTO) != (foto != null)
            || (imagen == Imagen.AVATAR ? datos.avatar() == null || !CatalogoAvatarCuidador.ids().contains(datos.avatar()) : datos.avatar() != null))
            throw invalido("imagen");

        if (foto != null) {
            comprobarRevision(activo(cuidadores.findById(id).orElseThrow(RechazoPerfil::noEncontrado)), datos.revision());
            return fotos.crear(Destino.CUIDADORES, foto, referencia -> guardar(id, datos, nombre, referencia));
        }
        return guardar(id, datos, nombre, null);
    }

    private Datos guardar(int id, Entrada entrada, String nombre, String referencia) {
        return transaccion.execute(status -> {
            var cuidador = cuidadores.bloquearParaCrearPerfil(id).orElseThrow(RechazoPerfil::noEncontrado);
            em.refresh(cuidador, LockModeType.PESSIMISTIC_WRITE);
            comprobarRevision(activo(cuidador), entrada.revision());
            String avatar = entrada.imagen() == Imagen.CONSERVAR ? cuidador.getAvatarId() : entrada.avatar();
            String anterior = cuidador.getFotoReferencia();
            String siguiente = entrada.imagen() == Imagen.CONSERVAR ? anterior : referencia;
            if (!Objects.equals(nombre, cuidador.getNombre()) || !Objects.equals(avatar, cuidador.getAvatarId())
                || !Objects.equals(anterior, siguiente)) {
                FotosPerfilService.confirmar(em, referencia);
                cuidador.setNombre(nombre);
                cuidador.setAvatarId(avatar);
                cuidador.setFotoReferencia(siguiente);
                cuidador.setRevisionDatosPersonales(cuidador.getRevisionDatosPersonales() + 1);
                FotosPerfilService.programarRetirada(em, anterior, siguiente, entorno, Destino.CUIDADORES);
            }
            return datos(cuidador);
        });
    }

    private static Datos datos(Cuidador cuidador) {
        return new Datos(cuidador.getEmail(), SesionCuidadorService.CuidadorResumen.de(cuidador));
    }

    private static Cuidador activo(Cuidador cuidador) {
        if (cuidador.getEstado() != EstadoCuidador.ACTIVO) throw RechazoPerfil.noEncontrado();
        return cuidador;
    }

    private static void comprobarRevision(Cuidador cuidador, int revision) {
        if (cuidador.getRevisionDatosPersonales() != revision)
            throw new RechazoPerfil("PERFIL_MODIFICADO", "Tus datos cambiaron. Consulta su versión actual.", null);
    }

    private static RechazoPerfil invalido(String campo) {
        return new RechazoPerfil("SOLICITUD_INVALIDA", "Revisa tus datos personales.", campo);
    }
}
