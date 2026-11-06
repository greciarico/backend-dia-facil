package dia_facil.backend.service.cuidadores;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.auth.SesionInvalidada;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccesibilidadCuidadorServiceTest {
    final EntityManager em = mock(EntityManager.class);
    final AccesibilidadCuidadorService service = new AccesibilidadCuidadorService(em);
    Cuidador owner() {
        var c = mock(Cuidador.class); when(c.getIdCuidador()).thenReturn(3); when(c.getEstado()).thenReturn(EstadoCuidador.ACTIVO);
        when(em.find(Cuidador.class, 3)).thenReturn(c); return c;
    }
    @Test void historicoYNuevoSinFilaTienenDefaultsSinEscriturasNiDatosDePerfil() {
        owner(); var actual = service.consultar(3);
        assertEquals(TamanoTexto.MEDIANO, actual.tamanoTexto()); assertFalse(actual.altoContraste());
        verify(em, never()).persist(any()); verify(em, never()).flush(); verify(em, never()).find(eq(ConfiguracionPerfil.class), any());
    }
    @Test void patchParcialBloqueaCuentaYConservaElOtroCampo() {
        var owner = owner(); var c = ConfiguracionCuidador.inicialPara(owner); c.setTamanoTexto(TamanoTexto.GRANDE);
        when(em.find(ConfiguracionCuidador.class, 3)).thenReturn(c);
        assertEquals(new AccesibilidadCuidadorService.Preferencias(TamanoTexto.GRANDE, true), service.actualizar(3, Map.of("altoContraste", true)));
        var order = inOrder(em); order.verify(em).find(Cuidador.class, 3); order.verify(em).refresh(owner, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).find(ConfiguracionCuidador.class, 3); order.verify(em).refresh(c); order.verify(em).flush();
        assertEquals(TamanoTexto.GRANDE, c.getTamanoTexto());
    }
    @Test void primeraEscrituraCreaSoloConfiguracionPropiaYResetConservaRelacion() {
        var owner = owner(); var actual = service.actualizar(3, Map.of("tamanoTexto", "PEQUENO"));
        assertEquals(TamanoTexto.PEQUENO, actual.tamanoTexto());
        verify(em).persist(argThat(value -> value instanceof ConfiguracionCuidador c && c.getCuidador() == owner));
        var c = ConfiguracionCuidador.inicialPara(owner); c.setTamanoTexto(TamanoTexto.GRANDE); c.setAltoContraste(true);
        when(em.find(ConfiguracionCuidador.class, 3)).thenReturn(c);
        assertEquals(new AccesibilidadCuidadorService.Preferencias(TamanoTexto.MEDIANO, false), service.restablecer(3));
        assertSame(owner, c.getCuidador()); verify(em, never()).find(eq(PerfilUsuario.class), any());
    }
    @Test void cuentaInactivaEliminadaONulaNoAccedeNiCreaConfiguracion() {
        var owner = owner(); when(owner.getEstado()).thenReturn(EstadoCuidador.INACTIVO);
        assertThrows(SesionInvalidada.class, () -> service.consultar(3)); assertThrows(SesionInvalidada.class, () -> service.actualizar(3, Map.of("altoContraste", true)));
        assertThrows(SesionInvalidada.class, () -> service.restablecer(99)); assertThrows(SesionInvalidada.class, () -> service.consultar(null));
        verify(em, never()).persist(any()); verify(em, never()).find(eq(ConfiguracionCuidador.class), any());
    }
    @Test void rechazaCamposDePerfilYAutoridadClienteNullEnumsYTiposAntesDeBd() {
        var nullValue = new HashMap<String, Object>(); nullValue.put("altoContraste", null);
        for (var input : List.<Map<String, Object>>of(Map.of(), Map.of("lecturaVoz", true), Map.of("idCuidador", 3),
            Map.of("recordatorioMinutos", 10), Map.of("tamanoTexto", 16), Map.of("tamanoTexto", "mediano"), Map.of("altoContraste", "true"), nullValue)) {
            assertThrows(AccesibilidadCuidadorService.SolicitudInvalida.class, () -> service.actualizar(3, input));
        }
        assertThrows(AccesibilidadCuidadorService.SolicitudInvalida.class, () -> service.actualizar(3, null)); verifyNoInteractions(em);
    }
    @Test void transaccionRevierteSiFallaLaEscritura() {
        owner(); var events = new ArrayList<String>();
        var manager = new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition d) {
                assertEquals(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED, d.getIsolationLevel()); events.add("begin");
            }
            protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) { events.add("commit"); }
            protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) { events.add("rollback"); }
        };
        var proxy = new org.springframework.aop.framework.ProxyFactory(service); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager, new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var tx = (AccesibilidadCuidadorService) proxy.getProxy(); tx.actualizar(3, Map.of("altoContraste", true));
        doThrow(new IllegalStateException("reserved")).when(em).flush(); assertThrows(IllegalStateException.class, () -> tx.restablecer(3));
        assertEquals(List.of("begin", "commit", "begin", "rollback"), events);
    }
}
