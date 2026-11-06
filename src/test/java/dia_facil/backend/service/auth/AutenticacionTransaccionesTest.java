package dia_facil.backend.service.auth;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static dia_facil.backend.service.auth.AutenticacionCuidadorTransaccionalServiceTest.*;
import static dia_facil.backend.service.auth.AutenticacionCuidadorService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticacionTransaccionesTest {
    @Test void falloAlEmitirSesionRevierteLaVerificacionDentroDeLaMismaTransaccion() {
        var f = new Fixture(); var codigo = f.codigo();
        var caregiver = dia_facil.backend.persistence.entity.Cuidador.crear(EMAIL, "Grecia");
        org.springframework.test.util.ReflectionTestUtils.setField(caregiver, "idCuidador", 3);
        when(f.cuidadores.findByEmail(EMAIL)).thenReturn(java.util.Optional.of(caregiver));
        var sessions = mock(SesionCuidadorService.class);
        doThrow(new IllegalStateException("Sesiones no disponibles")).when(sessions).emitir(any());
        org.springframework.test.util.ReflectionTestUtils.setField(f.servicio, "sesiones", sessions);
        var manager = new Manager();
        assertThrows(IllegalStateException.class, () -> proxy(f, manager).verificarConSesion(EMAIL, "000123"));
        assertEquals(List.of("begin", "rollback"), manager.events);
    }

    @Test void falloAlEmitirSesionRevierteElRegistroDentroDeLaMismaTransaccion() {
        var f = new Fixture(); var codigo = f.codigo(); codigo.setUsado(true);
        when(f.codigos.findById(7)).thenReturn(java.util.Optional.of(codigo));
        when(f.cuidadores.saveAndFlush(any())).thenAnswer(call -> {
            var c = (dia_facil.backend.persistence.entity.Cuidador) call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(c, "idCuidador", 3); return c;
        });
        var sessions = mock(SesionCuidadorService.class);
        doThrow(new IllegalStateException("Sesiones no disponibles")).when(sessions).emitir(3);
        org.springframework.test.util.ReflectionTestUtils.setField(f.servicio, "sesiones", sessions);
        var manager = new Manager();
        assertThrows(IllegalStateException.class, () -> proxy(f, manager).registrarConSesion(
            new CriptografiaAutenticacion.Comprobante(EMAIL, 7, NOW.plusSeconds(600)), "Grecia", null));
        assertEquals(List.of("begin", "rollback"), manager.events);
    }
    static class Manager extends AbstractPlatformTransactionManager {
        final List<String> events = new ArrayList<>();
        final ThreadLocal<State> actual = new ThreadLocal<>();
        static class State { boolean active; }
        @Override protected Object doGetTransaction() {
            return actual.get() == null ? new State() : actual.get();
        }
        @Override protected boolean isExistingTransaction(Object tx) { return ((State) tx).active; }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.ISOLATION_DEFAULT, definition.getIsolationLevel());
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRED, definition.getPropagationBehavior());
            ((State) tx).active = true;
            actual.set((State) tx);
            events.add("begin");
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
        @Override protected void doCleanupAfterCompletion(Object tx) { actual.remove(); }
    }

    static AutenticacionCuidadorTransaccionalService proxy(Fixture f, Manager manager) {
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        advice.afterPropertiesSet();
        var factory = new ProxyFactory(f.servicio);
        factory.setProxyTargetClass(true);
        factory.addAdvice(advice);
        return (AutenticacionCuidadorTransaccionalService) factory.getProxy();
    }

    @Test void rechazoFuncionalConIntentoIncrementadoConfirmaTransaccion() {
        var f = new Fixture();
        var codigo = f.codigo();
        var manager = new Manager();
        assertEquals(Motivo.CODIGO_INCORRECTO, proxy(f, manager).verificarCodigo(EMAIL, "999999").motivo());
        assertEquals(1, codigo.getIntentos());
        assertEquals(List.of("begin", "commit"), manager.events);
    }

    @Test void falloTecnicoRevierteTransaccionSinEnviar() {
        var f = new Fixture();
        when(f.codigos.save(any())).thenThrow(new IllegalStateException("Persistencia"));
        var manager = new Manager();
        var envio = mock(EnvioCodigoAcceso.class);
        var service = new AutenticacionCuidadorService(proxy(f, manager), f.crypto, envio);
        assertThrows(IllegalStateException.class, () -> service.solicitarCodigo(EMAIL));
        assertEquals(List.of("begin", "rollback"), manager.events);
        verifyNoInteractions(envio);
    }

    @Test void envioSeEjecutaDespuesDeCommitSinTransaccionExterior() {
        var f = new Fixture();
        var manager = new Manager();
        var service = new AutenticacionCuidadorService(proxy(f, manager), f.crypto,
            (email, codigo) -> manager.events.add("send"));
        service.solicitarCodigo(EMAIL);
        assertEquals(List.of("begin", "commit", "send"), manager.events);
    }

    @Test void requiredParticipaEnTransaccionExteriorYEsperaSuCommitParaEnviar() {
        var f = new Fixture();
        var manager = new Manager();
        var service = new AutenticacionCuidadorService(proxy(f, manager), f.crypto,
            (email, codigo) -> manager.events.add("send"));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.solicitarCodigo(EMAIL);
            assertEquals(List.of("begin"), manager.events);
        });
        assertEquals(List.of("begin", "commit", "send"), manager.events);
    }

    @Test void rollbackExteriorDescartaEnvioPendiente() {
        var f = new Fixture();
        var manager = new Manager();
        var envio = mock(EnvioCodigoAcceso.class);
        var service = new AutenticacionCuidadorService(proxy(f, manager), f.crypto, envio);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.solicitarCodigo(EMAIL);
            status.setRollbackOnly();
        });
        assertEquals(List.of("begin", "rollback"), manager.events);
        verifyNoInteractions(envio);
    }

    @Test void duplicadoSeTraduceSoloDespuesDeRollbackDelRegistro() {
        var f = new Fixture();
        var codigo = f.codigo();
        codigo.setUsado(true);
        when(f.codigos.findById(7)).thenReturn(java.util.Optional.of(codigo));
        var fallo = new org.springframework.dao.DataIntegrityViolationException("Duplicado",
            new java.sql.SQLException("Duplicate entry for key 'uq_cuidador_email'", "23000", 1062));
        when(f.cuidadores.saveAndFlush(any())).thenThrow(fallo);
        var manager = new Manager();
        var service = new AutenticacionCuidadorService(proxy(f, manager), f.crypto, mock(EnvioCodigoAcceso.class));
        assertEquals(Motivo.CUENTA_EXISTENTE, assertThrows(RechazoAutenticacion.class,
            () -> service.completarRegistro(f.crypto.emitirComprobante(EMAIL, 7), "Grecia")).getMotivo());
        assertEquals(List.of("begin", "rollback"), manager.events);
    }
}
