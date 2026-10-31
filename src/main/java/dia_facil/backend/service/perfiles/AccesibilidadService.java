package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.ConfiguracionPerfil;
import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.enums.TamanoTexto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccesibilidadService {
    public record Preferencias(TamanoTexto tamanoTexto, boolean altoContraste, boolean lecturaVoz) {
        static Preferencias de(ConfiguracionPerfil c) {
            return new Preferencias(c.getTamanoTexto(), c.getAltoContraste(), c.getLecturaVoz());
        }
    }
    private static final Preferencias INICIALES = new Preferencias(TamanoTexto.MEDIANO, false, true);
    private static final Set<String> CAMPOS = Set.of("tamanoTexto", "altoContraste", "lecturaVoz");
    private final EntityManager em;
    public AccesibilidadService(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public Preferencias consultar(Integer cuidador, Integer perfil) {
        return leer(perfil(cuidador, perfil, false));
    }
    /** Called only after E3 resolves the active profile from its authenticated credential. */
    @Transactional(readOnly = true)
    public Preferencias consultarUsuario(Integer perfil) { return leer(perfil(null, perfil, false)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Preferencias actualizar(Integer cuidador, Integer perfil, Map<String, Object> cambios) {
        validar(cambios);
        var p = perfil(cuidador, perfil, true);
        var c = configurar(p);
        if (cambios.containsKey("tamanoTexto")) c.setTamanoTexto(TamanoTexto.valueOf((String) cambios.get("tamanoTexto")));
        if (cambios.containsKey("altoContraste")) c.setAltoContraste((Boolean) cambios.get("altoContraste"));
        if (cambios.containsKey("lecturaVoz")) c.setLecturaVoz((Boolean) cambios.get("lecturaVoz"));
        em.flush();
        return Preferencias.de(c);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Preferencias restablecer(Integer cuidador, Integer perfil) {
        var c = configurar(perfil(cuidador, perfil, true));
        c.setTamanoTexto(INICIALES.tamanoTexto()); c.setAltoContraste(INICIALES.altoContraste()); c.setLecturaVoz(INICIALES.lecturaVoz());
        // recordatorioMinutos is intentionally preserved.
        em.flush(); return Preferencias.de(c);
    }
    private PerfilUsuario perfil(Integer cuidador, Integer id, boolean bloquear) {
        var p = id == null || id <= 0 ? null : em.find(PerfilUsuario.class, id);
        comprobar(p, cuidador);
        if (bloquear) { em.refresh(p, LockModeType.PESSIMISTIC_WRITE); comprobar(p, cuidador); }
        return p;
    }
    private void comprobar(PerfilUsuario p, Integer cuidador) {
        if (p == null || p.getEstado() != EstadoPerfil.ACTIVO || cuidador != null && !cuidador.equals(p.getIdCuidador())) throw RechazoPerfil.noEncontrado();
    }
    private Preferencias leer(PerfilUsuario perfil) {
        var c = em.find(ConfiguracionPerfil.class, perfil.getIdPerfilUsuario());
        return c == null ? INICIALES : Preferencias.de(c);
    }
    private ConfiguracionPerfil configurar(PerfilUsuario perfil) {
        var c = em.find(ConfiguracionPerfil.class, perfil.getIdPerfilUsuario());
        if (c == null) { c = ConfiguracionPerfil.inicialPara(perfil); em.persist(c); }
        else em.refresh(c); // Profile lock serializes updates; do not use a stale persistence-context snapshot.
        return c;
    }
    private void validar(Map<String, Object> cambios) {
        if (cambios == null || cambios.isEmpty() || !CAMPOS.containsAll(cambios.keySet())) throw invalido(null);
        cambios.forEach((campo, valor) -> {
            if (campo.equals("tamanoTexto")) {
                if (!(valor instanceof String texto)) throw invalido(campo);
                try { TamanoTexto.valueOf(texto); } catch (IllegalArgumentException ex) { throw invalido(campo); }
            } else if (!(valor instanceof Boolean)) throw invalido(campo);
        });
    }
    private RechazoPerfil invalido(String campo) { return new RechazoPerfil("SOLICITUD_INVALIDA", "Revisa los ajustes de accesibilidad.", campo); }
}
