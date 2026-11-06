package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.auth.SesionInvalidada;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CreacionPerfilTransaccionalService {
    private final CuidadorRepository cuidadores;
    private final PerfilUsuarioRepository perfiles;
    private final EntityManager em;
    private final GeneradorCodigoPerfil codigos;
    public CreacionPerfilTransaccionalService(CuidadorRepository cuidadores, PerfilUsuarioRepository perfiles,
            EntityManager em, GeneradorCodigoPerfil codigos) {
        this.cuidadores = cuidadores; this.perfiles = perfiles; this.em = em; this.codigos = codigos;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public PerfilResumen crear(Integer propietario, String nombre, String avatar) {
        var cuidador = cuidadores.bloquearParaCrearPerfil(propietario).orElseThrow(() -> new SesionInvalidada(false));
        // The authentication filter may have loaded this entity before the lock was obtained.
        em.refresh(cuidador, LockModeType.PESSIMISTIC_WRITE);
        if (cuidador.getEstado() != EstadoCuidador.ACTIVO) throw new SesionInvalidada(false);
        if (perfiles.countByIdCuidadorAndEstado(propietario, EstadoPerfil.ACTIVO) >= PoliticaPerfiles.MAXIMO_ACTIVOS) throw RechazoPerfil.limite();
        var perfil = PerfilUsuario.crear(cuidador, nombre, avatar, codigos.generar());
        em.persist(perfil);
        em.persist(ConfiguracionPerfil.inicialPara(perfil));
        em.flush();
        return new PerfilResumen(perfil.getIdPerfilUsuario(), perfil.getNombre(), perfil.getAvatar());
    }
}
