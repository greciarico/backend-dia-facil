package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VinculacionServiceTest {
    final PerfilUsuarioRepository perfiles = mock(PerfilUsuarioRepository.class);
    final DispositivoRepository dispositivos = mock(DispositivoRepository.class);
    final GeneradorCodigoPerfil codigos = mock(GeneradorCodigoPerfil.class);
    final EntityManager em = mock(EntityManager.class);
    final PerfilUsuario perfil = PerfilUsuario.crear(Cuidador.crear("test@example.invalid", "Test"), "Persona", null, "DF-482157");
    final VinculacionTransaccionalService tx = new VinculacionTransaccionalService(perfiles, dispositivos, codigos, em);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);
    final TypedQuery<LocalDateTime> clock = mock(TypedQuery.class);
    @BeforeEach void horaDeBd() {
        when(em.createQuery("select local datetime", LocalDateTime.class)).thenReturn(clock);
        when(clock.getSingleResult()).thenReturn(now);
    }

    @Test void consultaPropietarioActivoSinRenovarYCuentaSoloActivos() {
        when(perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        when(dispositivos.countByPerfilUsuario_IdPerfilUsuarioAndEstado(null, EstadoDispositivo.ACTIVO)).thenReturn(2L);
        assertEquals(new VinculacionResumen("Persona", "DF-482157", 2, 0), tx.consultar(3, 12));
        assertNull(perfil.getFechaRenovacionCodigo()); verifyNoInteractions(codigos); verify(em, never()).flush();
    }
    @Test void inexistenteAjenoEInactivoTienenLaMismaRespuesta() {
        for (int owner : List.of(3, 4)) {
            assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> tx.consultar(owner, 12)).codigo());
            assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> tx.renovar(owner, 12, "DF-482157")).codigo());
        }
        assertThrows(RechazoPerfil.class, () -> tx.consultar(3, -1)); verifyNoInteractions(codigos, em, dispositivos);
    }
    @Test void bloqueaReleeRenuevaConHoraBdYNoModificaDispositivos() {
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        when(codigos.generar()).thenReturn("DF-7KQ9W6H2M8V3X5RA");
        var query = mock(TypedQuery.class);
        var now = LocalDateTime.of(2026, 10, 22, 12, 0);
        when(em.createQuery("select local datetime", LocalDateTime.class)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(now);
        var result = tx.renovar(3, 12, "DF-482157");
        assertEquals("DF-7KQ9W6H2M8V3X5RA", result.codigoVinculacion()); assertEquals(now, perfil.getFechaRenovacionCodigo());
        assertEquals(60, result.renovacionEnSegundos());
        var order = inOrder(perfiles, em, codigos, dispositivos);
        order.verify(perfiles).bloquear(12, 3, EstadoPerfil.ACTIVO);
        order.verify(em).refresh(perfil, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).createQuery("select local datetime", LocalDateTime.class); order.verify(codigos).generar(); order.verify(em).flush();
        order.verify(dispositivos).countByPerfilUsuario_IdPerfilUsuarioAndEstado(null, EstadoDispositivo.ACTIVO);
        verifyNoMoreInteractions(dispositivos);
    }
    @Test void codigoEsperadoObsoletoNoRotaNiGeneraOtro() {
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        assertEquals("CODIGO_VINCULACION_CAMBIADO", assertThrows(RechazoPerfil.class, () -> tx.renovar(3, 12, "DF-000000")).codigo());
        assertEquals("DF-482157", perfil.getCodigoVinculacion()); verifyNoInteractions(codigos, dispositivos); verify(em, never()).flush();
    }
    @Test void colisionRevierteAntesDelReintentoYFalloAjenoNoSeReintenta() {
        var manager = new CreacionPerfilTransaccionesTest.Manager();
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        var query = mock(TypedQuery.class); when(em.createQuery("select local datetime", LocalDateTime.class)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(LocalDateTime.of(2026, 10, 22, 12, 0));
        when(codigos.generar()).thenReturn("DF-7KQ9W6H2M8V3X5RA", "DF-2KQ9W6H2M8V3X5RA");
        var proxy = new ProxyFactory(tx); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var service = new VinculacionService((VinculacionTransaccionalService) proxy.getProxy());
        var collision = new DataIntegrityViolationException("reserved", new ConstraintViolationException("reserved", new java.sql.SQLException("reserved"), "uq_perfil_codigo"));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> { if (calls.incrementAndGet() == 1) throw collision; return null; }).when(em).flush();
        // Simulate a fresh row after a database rollback.
        doAnswer(call -> { perfil.setCodigoVinculacion("DF-482157"); perfil.setFechaRenovacionCodigo(null); return null; }).when(em).refresh(perfil, LockModeType.PESSIMISTIC_WRITE);
        assertEquals("DF-2KQ9W6H2M8V3X5RA", service.renovar(3, 12, "DF-482157").codigoVinculacion());
        assertEquals(List.of("begin", "rollback", "begin", "commit"), manager.events);
        var transactions = mock(VinculacionTransaccionalService.class);
        when(transactions.renovar(3, 12, "DF-482157")).thenThrow(new IllegalStateException("reserved"));
        assertThrows(IllegalStateException.class, () -> new VinculacionService(transactions).renovar(3, 12, "DF-482157"));
        verify(transactions).renovar(3, 12, "DF-482157");
    }
    @Test void validaCondicionYAcotaColisiones() {
        var transactions = mock(VinculacionTransaccionalService.class); var service = new VinculacionService(transactions);
        for (var invalid : new String[] { null, "", " ", "x".repeat(21) }) assertThrows(RechazoPerfil.class, () -> service.renovar(3, 12, invalid));
        verifyNoInteractions(transactions);
        when(transactions.renovar(3, 12, "DF-482157")).thenThrow(new DataIntegrityViolationException("reserved",
            new ConstraintViolationException("reserved", new java.sql.SQLException("reserved"), "perfil_usuario.uq_perfil_codigo")));
        assertEquals("SERVICIO_NO_DISPONIBLE", assertThrows(RechazoPerfil.class, () -> service.renovar(3, 12, "DF-482157")).codigo());
        verify(transactions, times(3)).renovar(3, 12, "DF-482157");
    }
    @Test void consultaDevuelveEsperaCalculadaPorBdSinAlterarCodigoLegacy() {
        perfil.setFechaRenovacionCodigo(now.minusSeconds(15));
        when(perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        assertEquals(45, tx.consultar(3, 12).renovacionEnSegundos());
        assertEquals("DF-482157", perfil.getCodigoVinculacion()); verifyNoInteractions(codigos); verify(em, never()).flush();
    }
    @Test void rechazaAntesDeSesentaSinGenerarNiEscribirYPermiteEnElLimite() {
        when(perfiles.bloquear(12, 3, EstadoPerfil.ACTIVO)).thenReturn(Optional.of(perfil));
        perfil.setFechaRenovacionCodigo(now.minusSeconds(59));
        var error = assertThrows(RechazoPerfil.class, () -> tx.renovar(3, 12, "DF-482157"));
        assertEquals("RENOVACION_PREMATURA", error.codigo()); assertEquals(1L, error.renovacionEnSegundos());
        verifyNoInteractions(codigos, dispositivos); verify(em, never()).flush();
        assertEquals("DF-482157", perfil.getCodigoVinculacion());
        perfil.setFechaRenovacionCodigo(now.minusSeconds(60)); when(codigos.generar()).thenReturn("DF-7KQ9W6H2M8V3X5RA");
        assertEquals(60, tx.renovar(3, 12, "DF-482157").renovacionEnSegundos());
    }
    @Test void esperaSeRedondeaArribaYNoDependeDelRelojDelCliente() {
        assertEquals(0, PoliticaVinculacion.espera(null, now));
        assertEquals(1, PoliticaVinculacion.espera(now, now.plusSeconds(60).minusNanos(1)));
        assertEquals(0, PoliticaVinculacion.espera(now, now.plusSeconds(60)));
        assertEquals(0, PoliticaVinculacion.espera(now, now.plusSeconds(120)));
        assertEquals(70, PoliticaVinculacion.espera(now.plusSeconds(10), now));
    }
}
