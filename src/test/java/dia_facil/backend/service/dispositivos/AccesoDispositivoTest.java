package dia_facil.backend.service.dispositivos;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.auth.CriptografiaSesion;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccesoDispositivoTest {
    final EntityManager em = mock(EntityManager.class);
    final LocalDateTime now = LocalDateTime.of(2026,10,23,12,0);
    final CredencialDispositivo credential = CredencialDispositivo.preparar(new byte[32], now.plusMinutes(10));
    final PerfilUsuario profile = PerfilUsuario.crear(Cuidador.crear("test@example.invalid","Test"),"Persona",null,"DF-7KQ9W6H2M8V3X5RA");
    final AccesoDispositivoService service = new AccesoDispositivoService(em);
    @BeforeEach void setup() {
        service.habilitar();
        ReflectionTestUtils.setField(credential,"idCredencialDispositivo",1L); ReflectionTestUtils.setField(profile,"idPerfilUsuario",12);
        var clock=mock(TypedQuery.class);when(clock.getSingleResult()).thenReturn(now);
        when(em.createQuery("select local datetime",LocalDateTime.class)).thenReturn(clock);
        when(em.find(CredencialDispositivo.class,1L)).thenReturn(credential);
        when(em.find(CredencialDispositivo.class,1L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(credential);
        when(em.find(PerfilUsuario.class,12,LockModeType.PESSIMISTIC_WRITE)).thenReturn(profile);
        var profiles=mock(TypedQuery.class);when(profiles.setParameter(anyString(),any())).thenReturn(profiles);
        when(profiles.getResultStream()).thenAnswer(call->java.util.stream.Stream.of(12));
        when(em.createQuery(startsWith("select p.idPerfilUsuario"),eq(Integer.class))).thenReturn(profiles);
    }
    @Test void preparaSecretoAleatorioYGuardaSoloHash() {
        var capture=org.mockito.ArgumentCaptor.forClass(CredencialDispositivo.class);
        var result=service.preparar();verify(em).persist(capture.capture());
        assertTrue(CriptografiaSesion.valido(result.credencialDispositivo(),"du"));
        assertArrayEquals(CriptografiaSesion.hash(result.credencialDispositivo()),capture.getValue().getTokenHash());
        assertNull(capture.getValue().getDispositivo());assertEquals(now.plusMinutes(10),capture.getValue().getPreparacionHasta());
        assertFalse(result.toString().contains(result.credencialDispositivo()));
    }
    @Test void antesDeVerificarEsquemaYCodigosNoEmiteCredenciales() {
        assertEquals(503,assertThrows(RechazoDispositivo.class,()->new AccesoDispositivoService(em).preparar()).status());
        verify(em,never()).persist(any());
    }
    @Test void vinculaBajoBloqueoYConservaUnaCredencialConUnDispositivo() {
        var result=service.vincular(1L,profile.getCodigoVinculacion(),"  Android  ");
        assertEquals("VINCULADO",result.estado());assertEquals(12,result.perfil().idPerfilUsuario());
        assertNull(credential.getPreparacionHasta());assertSame(profile,credential.getDispositivo().getPerfilUsuario());
        assertEquals("Android",credential.getDispositivo().getNombre());assertEquals(EstadoDispositivo.ACTIVO,credential.getDispositivo().getEstado());
        var order=inOrder(em);order.verify(em).find(PerfilUsuario.class,12,LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(profile,LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).find(CredencialDispositivo.class,1L,LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(credential,LockModeType.PESSIMISTIC_WRITE);order.verify(em).persist(isA(Dispositivo.class));order.verify(em).flush();
        assertEquals(409,assertThrows(RechazoDispositivo.class,()->service.vincular(1L,profile.getCodigoVinculacion(),"Android")).status());
        verify(em,times(1)).persist(isA(Dispositivo.class));
    }
    @Test void mismo422ParaLegacyRenovadoInactivoYNoEncontrado() {
        for(String code:List.of("DF-482157","not-a-code")) assertEquals("CODIGO_VINCULACION_INVALIDO",assertThrows(RechazoDispositivo.class,()->service.vincular(1L,code,"Android")).codigo());
        profile.setEstado(EstadoPerfil.INACTIVO);
        assertEquals(422,assertThrows(RechazoDispositivo.class,()->service.vincular(1L,profile.getCodigoVinculacion(),"Android")).status());
        profile.setEstado(EstadoPerfil.ACTIVO);String old=profile.getCodigoVinculacion();
        doAnswer(call->{profile.setCodigoVinculacion("DF-2KQ9W6H2M8V3X5RA");return null;}).when(em).refresh(profile,LockModeType.PESSIMISTIC_WRITE);
        assertEquals(422,assertThrows(RechazoDispositivo.class,()->service.vincular(1L,old,"Android")).status());
        reset(em); // An absent result has the same public code.
        var clock=mock(TypedQuery.class);when(clock.getSingleResult()).thenReturn(now);
        when(em.createQuery("select local datetime",LocalDateTime.class)).thenReturn(clock);when(em.find(CredencialDispositivo.class,1L)).thenReturn(credential);
        var q=mock(TypedQuery.class);when(q.setParameter(anyString(),any())).thenReturn(q);when(q.getResultStream()).thenAnswer(c->java.util.stream.Stream.empty());
        when(em.createQuery(startsWith("select p.idPerfilUsuario"),eq(Integer.class))).thenReturn(q);
        assertEquals(422,assertThrows(RechazoDispositivo.class,()->service.vincular(1L,old,"Android")).status()); verify(em,never()).persist(any());
    }
    @Test void pendienteExpiraPeroVinculadaSoloSeRevocaPorEstado() {
        assertEquals("PENDIENTE",service.consultar(1L).estado());
        ReflectionTestUtils.setField(credential,"preparacionHasta",now);
        assertEquals(401,assertThrows(RechazoDispositivo.class,()->service.consultar(1L)).status());
        credential.vincular(Dispositivo.vincular(profile,"Test",now.minusYears(2)));
        assertEquals("VINCULADO",service.consultar(1L).estado());
        credential.getDispositivo().setEstado(EstadoDispositivo.DESVINCULADO);
        assertEquals(401,assertThrows(RechazoDispositivo.class,()->service.consultar(1L)).status());
        credential.getDispositivo().setEstado(EstadoDispositivo.ACTIVO);profile.setEstado(EstadoPerfil.INACTIVO);
        assertEquals(401,assertThrows(RechazoDispositivo.class,()->service.consultar(1L)).status());
    }
    @Test void tokenDeCuidadorNoAutenticaYNuncaSeUsaIdComoCredencial() {
        for(String token:List.of("a_"+"A".repeat(43),"r_"+"A".repeat(43),"12","DF-7KQ9W6H2M8V3X5RA"))
            assertEquals(401,assertThrows(RechazoDispositivo.class,()->service.autenticar(token)).status());
    }
    @Test void usuarioNoPuedeDesvincularDispositivoActivo() {
        credential.vincular(Dispositivo.vincular(profile,"Test",now));
        assertEquals(403,assertThrows(RechazoDispositivo.class,()->service.salir(1L)).status());
        assertEquals(EstadoDispositivo.ACTIVO,credential.getDispositivo().getEstado());
        assertNull(credential.getDispositivo().getFechaDesvinculacion());assertEquals(EstadoPerfil.ACTIVO,profile.getEstado());
        verify(em,never()).remove(profile);
    }
    @Test void agendaResuelvePerfilDesdeCredencialYRevalidaEstado() {
        var identity=new AccesoDispositivoService.Identidad(1L,999);
        assertEquals(401,assertThrows(RechazoDispositivo.class,()->service.perfilActivo(identity)).status());
        credential.vincular(Dispositivo.vincular(profile,"Test",now));
        assertEquals(12,service.perfilActivo(identity).idPerfilUsuario());
        profile.setEstado(EstadoPerfil.INACTIVO);
        assertEquals(401,assertThrows(RechazoDispositivo.class,()->service.perfilActivo(identity)).status());
    }
    @Test void pruebaPreviaReutilizaSoloDispositivoActivoDelPerfilResuelto() {
        String token="du_"+"D".repeat(43);
        var previous=CredencialDispositivo.preparar(new byte[32],now);
        previous.vincular(Dispositivo.vincular(profile,"Original",now.minusDays(1)));
        var query=mock(TypedQuery.class);when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.getResultStream()).thenAnswer(call->java.util.stream.Stream.of(previous));
        when(em.createQuery(startsWith("select c from CredencialDispositivo c join fetch"),eq(CredencialDispositivo.class))).thenReturn(query);
        var result=service.vincular(1L,profile.getCodigoVinculacion(),"Android",List.of(token));
        assertEquals(token,result.credencialDispositivo());assertEquals(12,result.perfil().idPerfilUsuario());
        assertNull(credential.getDispositivo());verify(em,never()).persist(isA(Dispositivo.class));
        assertFalse(result.toString().contains(token));
        previous.getDispositivo().setEstado(EstadoDispositivo.DESVINCULADO);
        var fresh=service.vincular(1L,profile.getCodigoVinculacion(),"Android",List.of(token));
        assertNull(fresh.credencialDispositivo());assertNotNull(credential.getDispositivo());
    }
    @Test void falloDeFlushRevierteActividadDeVinculacionCompleta() {
        class Manager extends AbstractPlatformTransactionManager {
            final List<String> events=new ArrayList<>();
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object t,TransactionDefinition d){assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,d.getIsolationLevel());events.add("begin");}
            protected void doCommit(DefaultTransactionStatus s){events.add("commit");}
            protected void doRollback(DefaultTransactionStatus s){events.add("rollback");}
        }
        var tx=new Manager();var proxy=new ProxyFactory(service);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        doThrow(new IllegalStateException("synthetic failure")).when(em).flush();
        assertThrows(IllegalStateException.class,()->((AccesoDispositivoService)proxy.getProxy()).vincular(1L,profile.getCodigoVinculacion(),"Test"));
        assertEquals(List.of("begin","rollback"),tx.events);
    }
}
