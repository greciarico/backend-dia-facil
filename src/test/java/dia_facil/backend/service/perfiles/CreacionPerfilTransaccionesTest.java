package dia_facil.backend.service.perfiles;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.auth.SesionInvalidada;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreacionPerfilTransaccionesTest {
    static class Manager extends AbstractPlatformTransactionManager {
        final List<String> events = new ArrayList<>();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel()); events.add("begin");
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
    static class Fixture {
        final CuidadorRepository caregivers = mock(CuidadorRepository.class);
        final PerfilUsuarioRepository profiles = mock(PerfilUsuarioRepository.class);
        final EntityManager em = mock(EntityManager.class);
        final GeneradorCodigoPerfil codes = mock(GeneradorCodigoPerfil.class);
        final Cuidador caregiver = Cuidador.crear("test@example.invalid", "Cuidador");
        final Manager manager = new Manager(); final CreacionPerfilTransaccionalService service;
        Fixture() {
            when(caregivers.bloquearParaCrearPerfil(3)).thenReturn(Optional.of(caregiver)); when(codes.generar()).thenReturn("DF-000123");
            var proxy = new ProxyFactory(new CreacionPerfilTransaccionalService(caregivers, profiles, em, codes)); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource())); service = (CreacionPerfilTransaccionalService) proxy.getProxy();
        }
    }
    @Test void bloqueaAntesDeContarYCreaActivoConConfiguracionEnUnCommit() {
        var f = new Fixture(); f.service.crear(3, "Persona", "person-01"); var order = inOrder(f.caregivers, f.em, f.profiles);
        order.verify(f.caregivers).bloquearParaCrearPerfil(3); order.verify(f.em).refresh(f.caregiver, LockModeType.PESSIMISTIC_WRITE);
        order.verify(f.profiles).countByIdCuidadorAndEstado(3, EstadoPerfil.ACTIVO);
        var profile = org.mockito.ArgumentCaptor.forClass(PerfilUsuario.class); order.verify(f.em).persist(profile.capture());
        var config = org.mockito.ArgumentCaptor.forClass(ConfiguracionPerfil.class); order.verify(f.em).persist(config.capture()); order.verify(f.em).flush();
        assertEquals(EstadoPerfil.ACTIVO, profile.getValue().getEstado()); assertSame(f.caregiver, profile.getValue().getCuidador());
        assertNull(profile.getValue().getFechaRenovacionCodigo()); assertEquals("DF-000123", profile.getValue().getCodigoVinculacion());
        assertSame(profile.getValue(), config.getValue().getPerfilUsuario()); assertEquals(TamanoTexto.MEDIANO, config.getValue().getTamanoTexto());
        assertFalse(config.getValue().getAltoContraste()); assertTrue(config.getValue().getLecturaVoz()); assertEquals(10, config.getValue().getRecordatorioMinutos());
        assertEquals(List.of("begin", "commit"), f.manager.events);
    }
    @Test void falloDeConfiguracionRevierteTodaLaTransaccion() {
        var f = new Fixture(); doThrow(new IllegalStateException("reserved")).when(f.em).persist(isA(ConfiguracionPerfil.class));
        assertThrows(IllegalStateException.class, () -> f.service.crear(3, "Persona", null)); assertEquals(List.of("begin", "rollback"), f.manager.events); verify(f.em, never()).flush();
    }
    @Test void cuatroActivosOCuentaInactivaNoInserta() {
        var f = new Fixture(); when(f.profiles.countByIdCuidadorAndEstado(3, EstadoPerfil.ACTIVO)).thenReturn(4L);
        assertEquals("LIMITE_PERFILES_ALCANZADO", assertThrows(RechazoPerfil.class, () -> f.service.crear(3, "Persona", null)).codigo()); verify(f.em, never()).persist(any());
        f.caregiver.setEstado(EstadoCuidador.INACTIVO); assertThrows(SesionInvalidada.class, () -> f.service.crear(3, "Persona", null)); verify(f.profiles, times(1)).countByIdCuidadorAndEstado(3, EstadoPerfil.ACTIVO);
    }
    @Test void colisionJpaRevierteAntesDeComenzarElReintento() {
        var f = new Fixture(); var attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            if (attempts.getAndIncrement() == 0) throw new org.hibernate.exception.ConstraintViolationException("reserved",
                new java.sql.SQLException("reserved", "23000", 1062), "uq_perfil_codigo");
            return null;
        }).when(f.em).persist(isA(PerfilUsuario.class));
        new CrearPerfilService(f.service).crear(3, "Persona", null);
        assertEquals(List.of("begin", "rollback", "begin", "commit"), f.manager.events);
    }
}
