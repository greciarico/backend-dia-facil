package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.enums.EstadoDispositivo;
import dia_facil.backend.persistence.repository.PerfilUsuarioRepository;
import dia_facil.backend.persistence.repository.DispositivoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class VinculacionTransaccionalService {
    private final PerfilUsuarioRepository perfiles;
    private final DispositivoRepository dispositivos;
    private final GeneradorCodigoPerfil codigos;
    private final EntityManager em;
    public VinculacionTransaccionalService(PerfilUsuarioRepository perfiles, DispositivoRepository dispositivos,
        GeneradorCodigoPerfil codigos, EntityManager em) {
        this.perfiles = perfiles; this.dispositivos = dispositivos; this.codigos = codigos; this.em = em;
    }
    @Transactional(readOnly = true)
    public VinculacionResumen consultar(Integer propietario, Integer id) {
        validarId(id);
        var perfil = perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(id, propietario, EstadoPerfil.ACTIVO)
            .orElseThrow(RechazoPerfil::noEncontrado);
        return resumen(perfil, ahora());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public VinculacionResumen renovar(Integer propietario, Integer id, String esperado) {
        validarId(id);
        var perfil = perfiles.bloquear(id, propietario, EstadoPerfil.ACTIVO).orElseThrow(RechazoPerfil::noEncontrado);
        // Read the current row even if the persistence context already contained this entity.
        em.refresh(perfil, LockModeType.PESSIMISTIC_WRITE);
        if (!perfil.getCodigoVinculacion().equals(esperado))
            throw new RechazoPerfil("CODIGO_VINCULACION_CAMBIADO", "El código cambió. Consulta el código actual antes de renovarlo.", null);
        var ahora = ahora();
        long espera = PoliticaVinculacion.espera(perfil.getFechaRenovacionCodigo(), ahora);
        if (espera > 0) throw RechazoPerfil.renovacionPrematura(espera);
        String nuevo = codigos.generar();
        // A fresh random code must differ from the one being invalidated.
        if (nuevo.equals(esperado)) throw new RechazoPerfil("SERVICIO_NO_DISPONIBLE", "No pudimos renovar el código. Inténtalo más tarde.", null);
        perfil.setCodigoVinculacion(nuevo);
        perfil.setFechaRenovacionCodigo(ahora);
        em.flush();
        return resumen(perfil, ahora);
    }
    private LocalDateTime ahora() { return em.createQuery("select local datetime", LocalDateTime.class).getSingleResult(); }
    private VinculacionResumen resumen(PerfilUsuario perfil, LocalDateTime ahora) {
        return new VinculacionResumen(perfil.getNombre(), perfil.getCodigoVinculacion(),
            dispositivos.countByPerfilUsuario_IdPerfilUsuarioAndEstado(perfil.getIdPerfilUsuario(), EstadoDispositivo.ACTIVO),
            PoliticaVinculacion.espera(perfil.getFechaRenovacionCodigo(), ahora));
    }
    private static void validarId(Integer id) { if (id == null || id <= 0) throw RechazoPerfil.noEncontrado(); }
}
