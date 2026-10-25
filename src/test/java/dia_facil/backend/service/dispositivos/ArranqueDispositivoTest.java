package dia_facil.backend.service.dispositivos;

import dia_facil.backend.configuration.DispositivoConfiguration;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArranqueDispositivoTest {
    @Test void verificaConSesionAbiertaYSoloHabilitaDespuesDeAuditoriaLimpia() throws Exception {
        var em=mock(EntityManager.class);var query=mock(Query.class);var session=mock(org.hibernate.Session.class);
        when(em.createQuery("select c from CredencialDispositivo c where 1=0")).thenReturn(query);
        when(em.unwrap(org.hibernate.Session.class)).thenAnswer(call->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());return session;
        });
        var tm=new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object t,TransactionDefinition d){}
            protected void doCommit(DefaultTransactionStatus s){}
            protected void doRollback(DefaultTransactionStatus s){}
        };
        var codes=mock(TransicionCodigosDispositivo.class);var access=mock(AccesoDispositivoService.class);
        var runner=new DispositivoConfiguration().comprobarE3(codes,em,access,tm);
        when(codes.auditar()).thenReturn(new TransicionCodigosDispositivo.Auditoria(1,1,0,0,true));
        assertThrows(IllegalStateException.class,()->runner.run(null));verify(access,never()).habilitar();
        when(codes.auditar()).thenReturn(new TransicionCodigosDispositivo.Auditoria(2,0,0,0,true));runner.run(null);
        verify(access).habilitar();assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
