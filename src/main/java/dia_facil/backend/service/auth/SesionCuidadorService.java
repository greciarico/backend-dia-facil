package dia_facil.backend.service.auth;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoCuidador;
import dia_facil.backend.persistence.repository.*;
import java.time.*;
import org.springframework.transaction.annotation.*;

/** Rotation rejection is returned, not thrown, so a reuse revocation commits. */
public class SesionCuidadorService {
    private final SesionCuidadorRepository sesiones;
    private final CredencialSesionCuidadorRepository credenciales;
    private final CuidadorRepository cuidadores;
    private final Clock clock;
    private final CriptografiaSesion crypto;
    private final Duration accessTtl, idleTtl, absoluteTtl;
    public SesionCuidadorService(SesionCuidadorRepository sesiones, CredencialSesionCuidadorRepository credenciales,
        CuidadorRepository cuidadores, Clock clock, CriptografiaSesion crypto, Duration accessTtl, Duration idleTtl, Duration absoluteTtl) {
        this.sesiones = sesiones; this.credenciales = credenciales; this.cuidadores = cuidadores;
        this.clock = clock; this.crypto = crypto; this.accessTtl = accessTtl; this.idleTtl = idleTtl; this.absoluteTtl = absoluteTtl;
    }
    private LocalDateTime ahora() {
        // Millisecond ISO dates are portable to the native JavaScript Date parser.
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), ZoneOffset.UTC);
    }
    private static LocalDateTime menor(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
    private static boolean vigente(SesionCuidador s, LocalDateTime ahora) {
        return s.getRevocadaEn() == null && ahora.isBefore(s.getInactividadHasta()) && ahora.isBefore(s.getVenceEn())
            && s.getCuidador().getEstado() == EstadoCuidador.ACTIVO;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Concesion emitir(Integer idCuidador) {
        var cuidador = cuidadores.findById(idCuidador).orElseThrow(() -> new SesionInvalidada(false));
        if (cuidador.getEstado() != EstadoCuidador.ACTIVO) throw new SesionInvalidada(false);
        var now = ahora();
        var sesion = sesiones.saveAndFlush(SesionCuidador.crear(cuidador, now, menor(now.plus(idleTtl), now.plus(absoluteTtl)), now.plus(absoluteTtl)));
        return nueva(sesion, 0, now);
    }
    private Concesion nueva(SesionCuidador s, int generacion, LocalDateTime now) {
        var access = crypto.generar("a"); var refresh = crypto.generar("r");
        var accessHasta = menor(now.plus(accessTtl), menor(s.getInactividadHasta(), s.getVenceEn()));
        credenciales.saveAndFlush(CredencialSesionCuidador.crear(s, generacion, CriptografiaSesion.hash(access), CriptografiaSesion.hash(refresh), accessHasta));
        return new Concesion(CuidadorResumen.de(s.getCuidador()), new Tokens("Bearer", access, accessHasta.toInstant(ZoneOffset.UTC),
            refresh, s.getInactividadHasta().toInstant(ZoneOffset.UTC), s.getVenceEn().toInstant(ZoneOffset.UTC)));
    }
    @Transactional
    public Concesion renovar(String refresh) {
        if (!CriptografiaSesion.valido(refresh, "r")) return null;
        var found = credenciales.findByRefreshHash(CriptografiaSesion.hash(refresh));
        if (found.isEmpty()) return null;
        var session = sesiones.bloquear(found.get().getSesion().getIdSesion()).orElse(null);
        var now = ahora();
        if (session == null || !vigente(session, now)) return null;
        // Re-read after acquiring the family lock; an overlapping rotation may have committed.
        var old = credenciales.findByRefreshHash(CriptografiaSesion.hash(refresh)).orElseThrow();
        var latest = credenciales.findFirstBySesionIdSesionOrderByGeneracionDesc(session.getIdSesion()).orElseThrow();
        if (old.getConsumidaEn() != null || !old.getIdCredencial().equals(latest.getIdCredencial())) {
            session.setRevocadaEn(now); sesiones.saveAndFlush(session); return null;
        }
        old.setConsumidaEn(now); credenciales.saveAndFlush(old);
        session.setInactividadHasta(menor(now.plus(idleTtl), session.getVenceEn())); sesiones.saveAndFlush(session);
        return nueva(session, old.getGeneracion() + 1, now);
    }
    @Transactional
    public void cerrar(String refresh) {
        if (!CriptografiaSesion.valido(refresh, "r")) return;
        credenciales.findByRefreshHash(CriptografiaSesion.hash(refresh)).ifPresent(c -> {
            sesiones.bloquear(c.getSesion().getIdSesion()).ifPresent(s -> {
                if (s.getRevocadaEn() == null) { s.setRevocadaEn(ahora()); sesiones.saveAndFlush(s); }
            });
        });
    }
    @Transactional(readOnly = true)
    public Identidad autenticar(String access) {
        if (!CriptografiaSesion.valido(access, "a")) throw new SesionInvalidada(true);
        var c = credenciales.findByAccessHash(CriptografiaSesion.hash(access)).orElseThrow(() -> new SesionInvalidada(true));
        if (!vigente(c.getSesion(), ahora())) throw new SesionInvalidada(false);
        if (!ahora().isBefore(c.getAccessHasta())) throw new SesionInvalidada(true);
        return new Identidad(c.getSesion().getIdSesion(), CuidadorResumen.de(c.getSesion().getCuidador()));
    }
    @Transactional
    public void limpiarFinalizadas() {
        for (var id : sesiones.finalizadasAntes(ahora().minusDays(7))) {
            var s = sesiones.bloquear(id).orElse(null);
            var limit = ahora().minusDays(7);
            if (s != null && (s.getVenceEn().isBefore(limit) || s.getInactividadHasta().isBefore(limit)
                || s.getRevocadaEn() != null && s.getRevocadaEn().isBefore(limit))) {
                credenciales.deleteBySesionIdSesion(id); sesiones.delete(s);
            }
        }
    }
    public record CuidadorResumen(Integer idCuidador, String nombre, String avatarId) {
        public static CuidadorResumen de(Cuidador c) { return new CuidadorResumen(c.getIdCuidador(), c.getNombre(), c.getAvatarId()); }
    }
    public record Tokens(String tokenType, String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt, Instant sessionExpiresAt) {
        @Override public String toString() { return "Tokens[contenido reservado]"; }
    }
    public record Concesion(CuidadorResumen cuidador, Tokens sesion) {
        @Override public String toString() { return "Concesion[contenido reservado]"; }
    }
    public record Identidad(Long idSesion, CuidadorResumen cuidador) implements java.security.Principal {
        @Override public String getName() { return cuidador.idCuidador().toString(); }
    }
}
