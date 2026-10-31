package dia_facil.backend.service.datosbase;

import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InicializadorDatosBaseTest {
    @Test void colisionConocidaRevierteAntesDeReintentarEnTransaccionNueva() {
        var tx = mock(PlatformTransactionManager.class); var primera = new SimpleTransactionStatus(); var segunda = new SimpleTransactionStatus();
        when(tx.getTransaction(any())).thenReturn(primera, segunda);
        var sync = mock(SincronizadorDatosBase.class); var esquema = mock(VerificadorEsquemaDatosBase.class);
        var esperado = new SincronizadorDatosBase.Resultado(1, 0, List.of());
        var colision = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23000", 1062), "uq_plantilla_paso_orden");
        when(sync.sincronizar(any())).thenThrow(colision).thenReturn(esperado);
        assertEquals(esperado, new InicializadorDatosBase(DatosBaseTest.fuente(), sync, esquema, tx).inicializar());
        var orden = inOrder(tx, sync, esquema);
        orden.verify(tx).getTransaction(argThat(d -> d.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
            && d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        orden.verify(esquema).verificar(); orden.verify(sync).sincronizar(any()); orden.verify(tx).rollback(primera);
        orden.verify(tx).getTransaction(any()); orden.verify(esquema).verificar(); orden.verify(sync).sincronizar(any()); orden.verify(tx).commit(segunda);
    }
    @Test void erroresDesconocidosYEsquemaInvalidoNoSeDisimulanNiReintentan() {
        var tx = mock(PlatformTransactionManager.class); var status = new SimpleTransactionStatus(); when(tx.getTransaction(any())).thenReturn(status);
        var sync = mock(SincronizadorDatosBase.class); var esquema = mock(VerificadorEsquemaDatosBase.class);
        doThrow(new IllegalStateException("Falta UNIQUE")).when(esquema).verificar();
        assertThrows(IllegalStateException.class, () -> new InicializadorDatosBase(DatosBaseTest.fuente(), sync, esquema, tx).inicializar());
        verifyNoInteractions(sync); verify(tx).rollback(status); verify(tx, times(1)).getTransaction(any());
        assertFalse(InicializadorDatosBase.reintentable(new ConstraintViolationException("other", new SQLException(), "fk_otra")));
        assertFalse(InicializadorDatosBase.reintentable(new SQLException("unavailable", "08001")));
        assertTrue(InicializadorDatosBase.reintentable(new ConstraintViolationException("duplicate", new SQLException(), "`diafacil`.`UQ_PLANTILLA_PASO_ORDEN`")));
    }
    @Test void maximoTresIntentosYBloqueosTransitoriosSonAcotados() {
        var tx = mock(PlatformTransactionManager.class); when(tx.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        var sync = mock(SincronizadorDatosBase.class); var esquema = mock(VerificadorEsquemaDatosBase.class);
        when(sync.sincronizar(any())).thenThrow(new RuntimeException(new SQLException("deadlock", "40001", 1213)));
        assertThrows(IllegalStateException.class, () -> new InicializadorDatosBase(DatosBaseTest.fuente(), sync, esquema, tx).inicializar());
        verify(tx, times(3)).rollback(any()); verify(tx, never()).commit(any()); verify(sync, times(3)).sincronizar(any());
    }
    @Test void recursoInvalidoNoAbreTransaccion() {
        var tx = mock(PlatformTransactionManager.class); var sync = mock(SincronizadorDatosBase.class); var esquema = mock(VerificadorEsquemaDatosBase.class);
        assertThrows(IllegalArgumentException.class, () -> new InicializadorDatosBase(new DatosBase(null, null, null), sync, esquema, tx).inicializar());
        verifyNoInteractions(tx, sync, esquema);
    }
}
