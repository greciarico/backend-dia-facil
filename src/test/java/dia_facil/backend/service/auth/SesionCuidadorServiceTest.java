package dia_facil.backend.service.auth;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.repository.*;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesionCuidadorServiceTest {
    static class Time extends Clock {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }
    static class Fixture {
        final Time time = new Time();
        final SesionCuidadorRepository sessions = mock(SesionCuidadorRepository.class);
        final CredencialSesionCuidadorRepository credentials = mock(CredencialSesionCuidadorRepository.class);
        final CuidadorRepository carers = mock(CuidadorRepository.class);
        final List<SesionCuidador> families = new ArrayList<>();
        final List<CredencialSesionCuidador> history = new ArrayList<>();
        final SesionCuidadorService service = new SesionCuidadorService(sessions, credentials, carers, time,
            new CriptografiaSesion(new SecureRandom()), Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(90));
        Fixture() {
            var caregiver = Cuidador.crear("test@example.com", "Grecia"); ReflectionTestUtils.setField(caregiver, "idCuidador", 3);
            when(carers.findById(3)).thenReturn(Optional.of(caregiver));
            when(sessions.saveAndFlush(any())).thenAnswer(call -> {
                SesionCuidador s = call.getArgument(0);
                if (s.getIdSesion() == null) { s.setIdSesion((long) families.size() + 1); families.add(s); }
                return s;
            });
            when(credentials.saveAndFlush(any())).thenAnswer(call -> {
                CredencialSesionCuidador c = call.getArgument(0);
                if (c.getIdCredencial() == null) { c.setIdCredencial((long) history.size() + 1); history.add(c); }
                return c;
            });
            when(sessions.bloquear(anyLong())).thenAnswer(call -> families.stream().filter(s -> s.getIdSesion().equals(call.getArgument(0))).findFirst());
            when(credentials.findByRefreshHash(any())).thenAnswer(call -> history.stream().filter(c -> Arrays.equals(c.getRefreshHash(), call.getArgument(0))).findFirst());
            when(credentials.findByAccessHash(any())).thenAnswer(call -> history.stream().filter(c -> Arrays.equals(c.getAccessHash(), call.getArgument(0))).findFirst());
            when(credentials.findFirstBySesionIdSesionOrderByGeneracionDesc(anyLong())).thenAnswer(call -> history.stream()
                .filter(c -> c.getSesion().getIdSesion().equals(call.getArgument(0))).max(Comparator.comparing(CredencialSesionCuidador::getGeneracion)));
        }
    }
    @Test void opacosSoloSePersistenComoHashesYExpiranEn15Minutos() {
        var f = new Fixture(); var grant = f.service.emitir(3);
        assertTrue(CriptografiaSesion.valido(grant.sesion().accessToken(), "a"));
        assertTrue(CriptografiaSesion.valido(grant.sesion().refreshToken(), "r"));
        assertArrayEquals(CriptografiaSesion.hash(grant.sesion().accessToken()), f.history.getFirst().getAccessHash());
        assertEquals(32, f.history.getFirst().getRefreshHash().length);
        assertFalse(grant.toString().contains(grant.sesion().refreshToken()));
        assertEquals(3, f.service.autenticar(grant.sesion().accessToken()).cuidador().idCuidador());
        f.time.advance(Duration.ofMinutes(15));
        assertTrue(assertThrows(SesionInvalidada.class, () -> f.service.autenticar(grant.sesion().accessToken())).renovable());
    }
    @Test void rotacionConservaHistorialYReutilizacionRevocaSoloSuFamilia() {
        var f = new Fixture(); var first = f.service.emitir(3); var other = f.service.emitir(3);
        var rotated = f.service.renovar(first.sesion().refreshToken());
        assertNotNull(rotated); assertNotEquals(first.sesion().refreshToken(), rotated.sesion().refreshToken());
        assertNotNull(f.history.getFirst().getConsumidaEn());
        assertNull(f.service.renovar(first.sesion().refreshToken()));
        assertFalse(assertThrows(SesionInvalidada.class, () -> f.service.autenticar(rotated.sesion().accessToken())).renovable());
        assertNotNull(f.service.autenticar(other.sesion().accessToken()));
    }
    @Test void inactividadYMaximoAbsolutoNoSeReinician() {
        var idle = new Fixture(); var expired = idle.service.emitir(3); idle.time.advance(Duration.ofDays(30));
        assertNull(idle.service.renovar(expired.sesion().refreshToken()));
        var f = new Fixture(); var grant = f.service.emitir(3); var absolute = grant.sesion().sessionExpiresAt();
        for (int i = 0; i < 3; i++) { f.time.advance(Duration.ofDays(29)); grant = f.service.renovar(grant.sesion().refreshToken()); assertNotNull(grant); assertEquals(absolute, grant.sesion().sessionExpiresAt()); }
        assertEquals(absolute, grant.sesion().refreshExpiresAt());
        f.time.advance(Duration.ofDays(3)); assertNull(f.service.renovar(grant.sesion().refreshToken()));
    }
    @Test void logoutConRefreshConsumidoEInactivaImpidenAcceso() {
        var f = new Fixture(); var original = f.service.emitir(3); var next = f.service.renovar(original.sesion().refreshToken());
        f.service.cerrar(original.sesion().refreshToken()); f.service.cerrar(original.sesion().refreshToken());
        assertThrows(SesionInvalidada.class, () -> f.service.autenticar(next.sesion().accessToken()));
        var active = new Fixture(); var grant = active.service.emitir(3);
        active.families.getFirst().getCuidador().setEstado(dia_facil.backend.persistence.enums.EstadoCuidador.INACTIVO);
        assertThrows(SesionInvalidada.class, () -> active.service.autenticar(grant.sesion().accessToken()));
        assertNull(active.service.renovar(grant.sesion().refreshToken()));
    }
    @Test void tokenDesconocidoOMalFormadoNoAbreSesion() {
        var f = new Fixture();
        assertNull(f.service.renovar("3")); assertThrows(SesionInvalidada.class, () -> f.service.autenticar("3"));
        assertDoesNotThrow(() -> f.service.cerrar("unknown"));
    }
    @Test void rechazoPorReutilizacionConfirmaRevocacionEnLaTransaccion() {
        var f = new Fixture(); var original = f.service.emitir(3); f.service.renovar(original.sesion().refreshToken());
        var manager = new AutenticacionTransaccionesTest.Manager();
        var factory = new org.springframework.aop.framework.ProxyFactory(f.service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy = (SesionCuidadorService) factory.getProxy();
        assertNull(proxy.renovar(original.sesion().refreshToken()));
        assertEquals(List.of("begin", "commit"), manager.events);
        assertNotNull(f.families.getFirst().getRevocadaEn());
    }
    @Test void generacionCambiadaMientrasEsperabaElLockNoSeAceptaAunqueEntidadEsteEnCache() {
        var f = new Fixture(); var original = f.service.emitir(3);
        when(f.sessions.bloquear(1L)).thenAnswer(call -> {
            var current = CredencialSesionCuidador.crear(f.families.getFirst(), 1,
                CriptografiaSesion.hash("a_" + "C".repeat(43)), CriptografiaSesion.hash("r_" + "C".repeat(43)),
                LocalDateTime.ofInstant(f.time.instant().plusSeconds(900), ZoneOffset.UTC));
            current.setIdCredencial(2L); f.history.add(current);
            return Optional.of(f.families.getFirst());
        });
        assertNull(f.service.renovar(original.sesion().refreshToken()));
        assertNotNull(f.families.getFirst().getRevocadaEn());
    }
}
