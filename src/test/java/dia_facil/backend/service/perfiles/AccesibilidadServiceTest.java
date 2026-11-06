package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccesibilidadServiceTest {
    final EntityManager em = mock(EntityManager.class);
    final AccesibilidadService service = new AccesibilidadService(em);
    PerfilUsuario perfil() {
        var p = mock(PerfilUsuario.class); when(p.getIdPerfilUsuario()).thenReturn(12); when(p.getIdCuidador()).thenReturn(3); when(p.getEstado()).thenReturn(EstadoPerfil.ACTIVO);
        when(em.find(PerfilUsuario.class, 12)).thenReturn(p); return p;
    }
    @Test void lecturaHistoricaSinConfiguracionDevuelveDefaultsSinEscribir() {
        perfil(); var expected = ConfiguracionPerfil.inicialPara(perfil());
        var actual = service.consultar(3, 12);
        assertEquals(expected.getTamanoTexto(), actual.tamanoTexto()); assertFalse(actual.altoContraste()); assertTrue(actual.lecturaVoz());
        verify(em, never()).persist(any()); verify(em, never()).flush();
        assertEquals(actual, service.consultarUsuario(12));
    }
    @Test void inexistenteAjenoEInactivoSonElMismo404SinLeerConfiguracion() {
        var p = perfil();
        for (var mode : List.of("ausente", "ajeno", "inactivo")) {
            when(em.find(PerfilUsuario.class, 12)).thenReturn(mode.equals("ausente") ? null : p);
            when(p.getIdCuidador()).thenReturn(mode.equals("ajeno") ? 4 : 3); when(p.getEstado()).thenReturn(mode.equals("inactivo") ? EstadoPerfil.INACTIVO : EstadoPerfil.ACTIVO);
            assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> service.consultar(3, 12)).codigo());
            assertThrows(RechazoPerfil.class, () -> service.actualizar(3, 12, Map.of("lecturaVoz", false)));
        }
        verify(em, never()).find(eq(ConfiguracionPerfil.class), any()); verify(em, never()).persist(any());
    }
    @Test void patchParcialBloqueaPerfilYConservaOtrosCamposYRecordatorio() {
        var p = perfil(); var c = ConfiguracionPerfil.inicialPara(p); c.setRecordatorioMinutos(15);
        when(em.find(ConfiguracionPerfil.class, 12)).thenReturn(c);
        var result = service.actualizar(3, 12, Map.of("altoContraste", true));
        assertTrue(result.altoContraste()); assertTrue(result.lecturaVoz()); assertEquals(TamanoTexto.MEDIANO, result.tamanoTexto()); assertEquals(15, c.getRecordatorioMinutos());
        var order = inOrder(em); order.verify(em).find(PerfilUsuario.class, 12); order.verify(em).refresh(p, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).find(ConfiguracionPerfil.class, 12); order.verify(em).refresh(c); order.verify(em).flush();
    }
    @Test void restablecerEsAtomicoYSoloCambiaAccesibilidad() {
        var p = perfil(); var c = ConfiguracionPerfil.inicialPara(p); c.setTamanoTexto(TamanoTexto.GRANDE); c.setAltoContraste(true); c.setLecturaVoz(false); c.setRecordatorioMinutos(30);
        when(em.find(ConfiguracionPerfil.class, 12)).thenReturn(c);
        var result = service.restablecer(3, 12);
        assertEquals(TamanoTexto.MEDIANO, result.tamanoTexto()); assertFalse(result.altoContraste()); assertTrue(result.lecturaVoz()); assertEquals(30, c.getRecordatorioMinutos());
        verify(em).refresh(p, LockModeType.PESSIMISTIC_WRITE); verify(em).flush();
    }
    @Test void creaConfiguracionFaltanteSoloAlEscribir() {
        var p = perfil(); var result = service.actualizar(3, 12, Map.of("tamanoTexto", "GRANDE", "lecturaVoz", false));
        assertEquals(TamanoTexto.GRANDE, result.tamanoTexto()); assertFalse(result.lecturaVoz());
        verify(em).persist(argThat(c -> c instanceof ConfiguracionPerfil config && config.getPerfilUsuario() == p && config.getRecordatorioMinutos() == 10));
    }
    @Test void rechazaVacioNullDesconocidosTiposYEnumAntesDeTocarBd() {
        List<Map<String, Object>> invalid = new ArrayList<>(); invalid.add(null); invalid.add(Map.of());
        invalid.add(Map.of("idCuidador", 3)); invalid.add(Map.of("recordatorioMinutos", 20)); invalid.add(Map.of("tamanoTexto", "mediano"));
        invalid.add(Map.of("tamanoTexto", 16)); invalid.add(Map.of("lecturaVoz", "true")); invalid.add(Map.of("altoContraste", 1));
        var withNull = new HashMap<String, Object>(); withNull.put("lecturaVoz", null); invalid.add(withNull);
        for (var changes : invalid) assertEquals("SOLICITUD_INVALIDA", assertThrows(RechazoPerfil.class, () -> service.actualizar(3, 12, changes)).codigo());
        verifyNoInteractions(em);
    }
    @Test void escrituraYRestablecimientoUsanTransaccionYFalloRevierte() {
        perfil(); var events = new ArrayList<String>();
        var manager = new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) {
                assertEquals(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel()); events.add("begin");
            }
            protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) { events.add("commit"); }
            protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) { events.add("rollback"); }
        };
        var proxy = new org.springframework.aop.framework.ProxyFactory(service); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional = (AccesibilidadService) proxy.getProxy();
        transactional.actualizar(3, 12, Map.of("lecturaVoz", false));
        doThrow(new IllegalStateException("reserved")).when(em).flush();
        assertThrows(IllegalStateException.class, () -> transactional.restablecer(3, 12));
        assertEquals(List.of("begin", "commit", "begin", "rollback"), events);
    }
}
