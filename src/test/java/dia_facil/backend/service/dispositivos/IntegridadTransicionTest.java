package dia_facil.backend.service.dispositivos;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.perfiles.GeneradorCodigoPerfil;
import jakarta.persistence.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegridadTransicionTest {
    static final String CURRENT="DF-7KQ9W6H2M8V3X5RA", NEW="DF-2KQ9W6H2M8V3X5RA";
    final EntityManager em=mock(EntityManager.class);
    final org.hibernate.Session session=mock(org.hibernate.Session.class);
    final GeneradorCodigoPerfil generator=mock(GeneradorCodigoPerfil.class);
    final List<PerfilUsuario> profiles=new ArrayList<>();

    IntegridadTransicionTest() {
        when(em.unwrap(org.hibernate.Session.class)).thenReturn(session);
        when(session.doReturningWork(any())).thenReturn(true);
        var codes=mock(TypedQuery.class);
        when(em.createQuery("select p.codigoVinculacion from PerfilUsuario p",String.class)).thenReturn(codes);
        when(codes.getResultList()).thenAnswer(call->profiles.stream().map(PerfilUsuario::getCodigoVinculacion).toList());
        var locked=mock(TypedQuery.class);
        when(em.createQuery("select p from PerfilUsuario p order by p.idPerfilUsuario",PerfilUsuario.class)).thenReturn(locked);
        when(locked.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(locked);when(locked.getResultList()).thenReturn(profiles);
        var count=mock(TypedQuery.class);
        when(em.createQuery(startsWith("select count(p)"),eq(Long.class))).thenReturn(count);
        when(count.setParameter(anyString(),any())).thenReturn(count);when(count.getSingleResult()).thenReturn(0L);
        when(generator.generar()).thenReturn(NEW);
    }
    PerfilUsuario profile(String code) {
        var result=PerfilUsuario.crear(Cuidador.crear("test@example.invalid","Test"),"Nombre",null,code);
        profiles.add(result);return result;
    }
    TransicionCodigosDispositivo service() {return new TransicionCodigosDispositivo(em,generator);}

    @Test void auditoriaCuentaDuplicadosSinExponerCodigos() {
        profile(CURRENT);profile("DF-123456");profile("unexpected");profile(CURRENT);
        var result=service().auditar();
        assertEquals(new TransicionCodigosDispositivo.Auditoria(2,1,1,1,true),result);
        assertEquals(4,result.total());assertFalse(result.toString().contains(CURRENT));verify(em,never()).flush();
    }
    @Test void rechazaInesperadosDuplicadosONoUniqueAntesDeModificar() {
        var legacy=profile("DF-123456");var other=profile("unexpected");
        assertThrows(IllegalStateException.class,()->service().aplicar());
        other.setCodigoVinculacion("DF-123456");assertThrows(IllegalStateException.class,()->service().aplicar());
        other.setCodigoVinculacion(CURRENT);when(session.doReturningWork(any())).thenReturn(false);
        assertThrows(IllegalStateException.class,()->service().aplicar());
        assertEquals("DF-123456",legacy.getCodigoVinculacion());verifyNoInteractions(generator);verify(em,never()).flush();
    }
    @Test void reintentaCandidatosEnColisionSinCambiarCodigosVigentes() {
        var legacy=profile("DF-123456");profile(CURRENT);
        when(generator.generar()).thenReturn(CURRENT,CURRENT,NEW);
        assertEquals(1,service().aplicar());assertEquals(NEW,legacy.getCodigoVinculacion());verify(generator,times(3)).generar();
    }
    @Test void agotamientoDeColisionesNoEscribe() {
        var legacy=profile("DF-123456");profile(CURRENT);when(generator.generar()).thenReturn(CURRENT);
        assertThrows(IllegalStateException.class,()->service().aplicar());
        assertEquals("DF-123456",legacy.getCodigoVinculacion());verify(generator,times(3)).generar();verify(em,never()).flush();
    }
    @Test void evitaReservarMismoCandidatoParaDosPerfiles() {
        profile("DF-123456");profile("DF-654321");
        when(generator.generar()).thenReturn(NEW,NEW,"DF-3KQ9W6H2M8V3X5RA");
        assertEquals(2,service().aplicar());assertNotEquals(profiles.get(0).getCodigoVinculacion(),profiles.get(1).getCodigoVinculacion());
        verify(generator,times(3)).generar();
    }
    @Test void uniqueRechazadoPorBdRevierteSinReintentarEnTransaccionRota() {
        profile("DF-123456");var tx=new Manager();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic unique failure")).when(em).flush();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->transactional(tx).aplicar());
        assertEquals(List.of("begin","rollback"),tx.events);verify(generator,times(1)).generar();
    }
    @Test void falloDeComprobacionPosteriorAlFlushTambienRevierte() {
        var legacy=profile("DF-123456");var tx=new Manager();
        doAnswer(call->{legacy.setFechaRenovacionCodigo(LocalDateTime.of(2026,10,23,12,0));return null;}).when(em).flush();
        assertThrows(IllegalStateException.class,()->transactional(tx).aplicar());
        assertEquals(List.of("begin","rollback"),tx.events);
    }
    TransicionCodigosDispositivo transactional(Manager manager) {
        var proxy=new ProxyFactory(service());proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (TransicionCodigosDispositivo)proxy.getProxy();
    }
    static class Manager extends AbstractPlatformTransactionManager {
        final List<String> events=new ArrayList<>();
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object t,TransactionDefinition d){assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,d.getIsolationLevel());events.add("begin");}
        protected void doCommit(DefaultTransactionStatus s){events.add("commit");}
        protected void doRollback(DefaultTransactionStatus s){events.add("rollback");}
    }
    @Test void uniqueDebeSerDeCodigoSoloSinDdlNiAceptarIndiceCompuesto() throws Exception {
        var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);var rows=mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);when(connection.getCatalog()).thenReturn("test");
        when(metadata.getIndexInfo("test",null,"perfil_usuario",true,false)).thenReturn(rows);
        when(rows.next()).thenReturn(true,false);when(rows.getString("INDEX_NAME")).thenReturn("uq_perfil_codigo");
        when(rows.getString("COLUMN_NAME")).thenReturn("codigo_vinculacion");
        assertTrue(TransicionCodigosDispositivo.codigoUnico(connection));
        when(rows.next()).thenReturn(true,true,false);when(rows.getString("COLUMN_NAME")).thenReturn("codigo_vinculacion","id_cuidador");
        assertFalse(TransicionCodigosDispositivo.codigoUnico(connection));
        verify(connection,never()).createStatement();verify(connection,never()).prepareStatement(anyString());
    }
}
