package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreacionActividadTransaccionesTest {
    static class Manager extends AbstractPlatformTransactionManager {
        final List<String> events = new ArrayList<>();
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel()); events.add("begin");
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
    @Test void falloEnSegundoPasoRevierteLaUnicaTransaccionDeTodaLaDefinicion() {
        var perfiles = mock(PerfilUsuarioRepository.class); var iconos = mock(IconoActividadRepository.class);
        var em = mock(EntityManager.class); var pictos = mock(PictogramaRepository.class); var manager = new Manager();
        when(perfiles.findByIdPerfilUsuarioAndIdCuidadorAndEstado(12,3,EstadoPerfil.ACTIVO))
            .thenReturn(Optional.of(PerfilUsuario.crear(null,"P",null,"DF-000001")));
        when(iconos.findByCodigo("bread")).thenReturn(Optional.of(IconoActividad.crear("bread","Pan")));
        doAnswer(call -> { if (call.getArgument(0) instanceof PasoActividad paso && paso.getOrden() == 2) throw new IllegalStateException("reserved"); return null; }).when(em).persist(any());
        var proxy = new ProxyFactory(new CreacionActividadTransaccionalService(perfiles,iconos,pictos,em));
        proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var service = (CreacionActividadTransaccionalService)proxy.getProxy();
        var entrada = new CrearActividad("A","07:00","bread",List.of(new CrearActividad.Paso("Uno",123),new CrearActividad.Paso("Dos",null))).validado();
        assertThrows(IllegalStateException.class, () -> service.crear(3,12,entrada,Map.of(123,PictogramaArasaac.de(123,"Oficial"))));
        assertEquals(List.of("begin","rollback"),manager.events); verify(em,never()).flush();
        verify(em).persist(isA(Pictograma.class)); verify(em).persist(isA(Actividad.class)); verify(em,times(2)).persist(isA(PasoActividad.class));
    }
}
