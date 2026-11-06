package dia_facil.backend.service.cuidadores;

import dia_facil.backend.persistence.entity.ConfiguracionCuidador;
import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.enums.EstadoCuidador;
import dia_facil.backend.persistence.enums.TamanoTexto;
import dia_facil.backend.service.auth.SesionInvalidada;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccesibilidadCuidadorService {
    public record Preferencias(TamanoTexto tamanoTexto, boolean altoContraste) {
        static Preferencias de(ConfiguracionCuidador c) { return new Preferencias(c.getTamanoTexto(), c.getAltoContraste()); }
    }
    public static class SolicitudInvalida extends RuntimeException {
        public SolicitudInvalida() { super("Revisa los ajustes de accesibilidad."); }
    }
    private static final Preferencias INICIALES = new Preferencias(TamanoTexto.MEDIANO, false);
    private static final Set<String> CAMPOS = Set.of("tamanoTexto", "altoContraste");
    private final EntityManager em;
    public AccesibilidadCuidadorService(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public Preferencias consultar(Integer id) {
        cuidador(id, false);
        var c = em.find(ConfiguracionCuidador.class, id);
        return c == null ? INICIALES : Preferencias.de(c);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Preferencias actualizar(Integer id, Map<String, Object> cambios) {
        validar(cambios);
        var c = configurar(cuidador(id, true));
        if (cambios.containsKey("tamanoTexto")) c.setTamanoTexto(TamanoTexto.valueOf((String) cambios.get("tamanoTexto")));
        if (cambios.containsKey("altoContraste")) c.setAltoContraste((Boolean) cambios.get("altoContraste"));
        em.flush(); return Preferencias.de(c);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Preferencias restablecer(Integer id) {
        var c = configurar(cuidador(id, true));
        c.setTamanoTexto(INICIALES.tamanoTexto()); c.setAltoContraste(INICIALES.altoContraste());
        em.flush(); return Preferencias.de(c);
    }
    private Cuidador cuidador(Integer id, boolean bloquear) {
        var c = id == null || id <= 0 ? null : em.find(Cuidador.class, id);
        comprobar(c);
        if (bloquear) { em.refresh(c, LockModeType.PESSIMISTIC_WRITE); comprobar(c); }
        return c;
    }
    private void comprobar(Cuidador c) { if (c == null || c.getEstado() != EstadoCuidador.ACTIVO) throw new SesionInvalidada(false); }
    private ConfiguracionCuidador configurar(Cuidador cuidador) {
        var c = em.find(ConfiguracionCuidador.class, cuidador.getIdCuidador());
        if (c == null) { c = ConfiguracionCuidador.inicialPara(cuidador); em.persist(c); }
        else em.refresh(c);
        return c;
    }
    private void validar(Map<String, Object> cambios) {
        if (cambios == null || cambios.isEmpty() || !CAMPOS.containsAll(cambios.keySet())) throw new SolicitudInvalida();
        cambios.forEach((campo, valor) -> {
            if (campo.equals("tamanoTexto")) {
                if (!(valor instanceof String texto)) throw new SolicitudInvalida();
                try { TamanoTexto.valueOf(texto); } catch (IllegalArgumentException ex) { throw new SolicitudInvalida(); }
            } else if (!(valor instanceof Boolean)) throw new SolicitudInvalida();
        });
    }
}
