package dia_facil.backend.service.push;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.persistence.enums.*;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class RegistroPushService {
    private final EntityManager em;
    private final PushProperties config;
    public RegistroPushService(EntityManager em, PushProperties config) { this.em = em; this.config = config; }

    public record Peticion(UUID idInstalacion, String expoToken, String plataforma) {
        @Override public String toString() { return "Peticion[reservada]"; }
    }
    public record Estado(boolean activo, Integer idRegistro, Long generacion, Integer idCuidador, String proyectoEas) {}
    public record Versionado(Estado estado, String etag) {}

    public static boolean vigente(SesionCuidador s, LocalDateTime now) {
        return s != null && s.getRevocadaEn() == null && s.getVenceEn().isAfter(now)
            && s.getInactividadHasta().isAfter(now) && s.getCuidador().getEstado() == EstadoCuidador.ACTIVO;
    }
    private SesionCuidador sesion(Identidad p) {
        if (p == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var s = em.find(SesionCuidador.class, p.idSesion(), LockModeType.PESSIMISTIC_WRITE);
        if (!vigente(s, LocalDateTime.now(ZoneOffset.UTC)) || !s.getCuidador().getIdCuidador().equals(p.cuidador().idCuidador()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return s;
    }
    private void validarId(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    static void validarPrecondicion(String tag) {
        if (tag == null) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED);
        if (!tag.matches("\"[0-9a-f]{64}\"")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    static String etag(UUID id, Long session, long version) {
        try {
            var input = id + "\n" + session + "\n" + version;
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8))) + "\"";
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private ControlRegistroPush controlar(UUID id, SesionCuidador s, String expected) {
        // Upsert + locking read also serialize the first mutation when no token/control exists.
        em.createNativeQuery("INSERT INTO control_registro_push (id_instalacion, version) VALUES (:id, 0) "
            + "ON DUPLICATE KEY UPDATE id_instalacion = id_instalacion")
            .setParameter("id", id.toString()).executeUpdate();
        var control = em.find(ControlRegistroPush.class, id.toString(), LockModeType.PESSIMISTIC_WRITE);
        if (!etag(id, s.getIdSesion(), control.getVersion()).equals(expected))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED);
        return control;
    }
    private TokenPush buscar(UUID id) {
        return em.createQuery("select t from TokenPush t where t.idInstalacion=:id", TokenPush.class)
            .setParameter("id", id.toString()).setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream().findFirst().orElse(null);
    }
    @Transactional
    public Versionado registrar(Identidad p, Peticion b, String expected) {
        validarPrecondicion(expected);
        if (!config.enabled() || config.projectId() == null || config.projectId().isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (b == null || b.idInstalacion() == null || !"ANDROID".equals(b.plataforma()) || b.expoToken() == null
            || !b.expoToken().matches("(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{1,200}\\]"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var s = sesion(p);
        var control = controlar(b.idInstalacion(), s, expected);
        var t = buscar(b.idInstalacion());
        var same = em.createQuery("select t from TokenPush t where t.expoToken=:token", TokenPush.class)
            .setParameter("token", b.expoToken()).setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream().findFirst().orElse(null);
        if (same != null && same != t) {
            if (t != null) { t.setActivo(false); t.setIdInstalacion(null); t.setGeneracion(t.getGeneracion()+1); em.flush(); }
            t = same;
        }
        if (t == null) t = TokenPush.nuevo();
        else if (!t.isActivo() || !b.expoToken().equals(t.getExpoToken()) || t.getSesion() == null
            || !s.getIdSesion().equals(t.getSesion().getIdSesion()) || !config.projectId().equals(t.getProyectoEas())
            || !b.idInstalacion().toString().equals(t.getIdInstalacion())) t.setGeneracion(t.getGeneracion()+1);
        t.setCuidador(s.getCuidador()); t.setSesion(s); t.setExpoToken(b.expoToken());
        t.setIdInstalacion(b.idInstalacion().toString()); t.setProyectoEas(config.projectId());
        t.setPlataforma(Plataforma.ANDROID); t.setActivo(true);
        if (t.getIdTokenPush() == null) em.persist(t);
        control.avanzar(); em.flush();
        return new Versionado(dto(t, true), etag(b.idInstalacion(), s.getIdSesion(), control.getVersion()));
    }
    @Transactional
    public Versionado consultar(Identidad p, UUID id) {
        validarId(id);
        var s = sesion(p);
        // Same lock order as writes: session -> installation control -> token.
        var control = em.find(ControlRegistroPush.class, id.toString(), LockModeType.PESSIMISTIC_READ);
        var t = buscar(id);
        var state = propio(t, s) ? dto(t, t.isActivo()) : new Estado(false, null, null, p.cuidador().idCuidador(), config.projectId());
        return new Versionado(state, etag(id, s.getIdSesion(), control == null ? 0 : control.getVersion()));
    }
    @Transactional
    public String eliminar(Identidad p, UUID id, String expected) {
        validarPrecondicion(expected); validarId(id);
        var s = sesion(p);
        var control = controlar(id, s, expected);
        var t = buscar(id);
        if (propio(t, s) && t.isActivo()) { t.setActivo(false); t.setGeneracion(t.getGeneracion()+1); }
        // Always advance, including absent/inactive/foreign-session tokens. Never mutate foreign ownership.
        control.avanzar(); em.flush();
        return etag(id, s.getIdSesion(), control.getVersion());
    }
    private boolean propio(TokenPush t, SesionCuidador s) {
        return t != null && t.getSesion() != null && s.getIdSesion().equals(t.getSesion().getIdSesion());
    }
    private Estado dto(TokenPush t, boolean activo) {
        return new Estado(activo, t.getIdTokenPush(), t.getGeneracion(), t.getCuidador().getIdCuidador(), t.getProyectoEas());
    }
}
