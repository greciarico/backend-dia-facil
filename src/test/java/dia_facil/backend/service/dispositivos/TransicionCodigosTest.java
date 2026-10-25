package dia_facil.backend.service.dispositivos;

import dia_facil.backend.configuration.DispositivoConfiguration;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.perfiles.GeneradorCodigoPerfil;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransicionCodigosTest {
    @Test void auditoriaNoEscribeNiExponeCodigos() {
        var em=mock(EntityManager.class);var query=mock(TypedQuery.class);
        when(em.createQuery("select p.codigoVinculacion from PerfilUsuario p",String.class)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("DF-7KQ9W6H2M8V3X5RA","DF-123456","unexpected"));
        unique(em);
        var result=new TransicionCodigosDispositivo(em,new GeneradorCodigoPerfil()).auditar();
        assertEquals(new TransicionCodigosDispositivo.Auditoria(1,1,1,0,true),result);
        verify(em,never()).flush();verify(em,never()).persist(any());
    }
    @Test void cambiaSoloCodigoLegacyYSegundaEjecucionEsIdempotente() {
        var em=mock(EntityManager.class);var profiles=mock(TypedQuery.class);var count=mock(TypedQuery.class);
        unique(em);
        var owner=Cuidador.crear("test@example.invalid","Test");
        var legacy=PerfilUsuario.crear(owner,"Nombre",null,"DF-123456");
        var current=PerfilUsuario.crear(owner,"Otro",null,"DF-7KQ9W6H2M8V3X5RA");
        var historic=PerfilUsuario.crear(owner,"Histórico",null,"DF-654321");
        var date=LocalDateTime.of(2026,10,23,12,0);historic.setFechaRenovacionCodigo(date);current.setFechaRenovacionCodigo(date);
        var codes=mock(TypedQuery.class);
        when(em.createQuery("select p.codigoVinculacion from PerfilUsuario p",String.class)).thenReturn(codes);
        when(codes.getResultList()).thenAnswer(call->List.of(legacy.getCodigoVinculacion(),current.getCodigoVinculacion(),historic.getCodigoVinculacion()));
        when(em.createQuery("select p from PerfilUsuario p order by p.idPerfilUsuario",PerfilUsuario.class)).thenReturn(profiles);
        when(profiles.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(profiles);when(profiles.getResultList()).thenReturn(List.of(legacy,current,historic));
        when(em.createQuery(startsWith("select count(p)"),eq(Long.class))).thenReturn(count);
        when(count.setParameter(anyString(),any())).thenReturn(count);when(count.getSingleResult()).thenReturn(0L);
        var service=new TransicionCodigosDispositivo(em,new GeneradorCodigoPerfil());
        assertEquals(2,service.aplicar());assertEquals(0,service.aplicar());
        assertTrue(legacy.getCodigoVinculacion().matches(AccesoDispositivoService.FORMATO));assertNull(legacy.getFechaRenovacionCodigo());
        assertEquals("DF-7KQ9W6H2M8V3X5RA",current.getCodigoVinculacion());assertEquals(date,current.getFechaRenovacionCodigo());
        assertEquals(date,historic.getFechaRenovacionCodigo());
        assertSame(owner,legacy.getCuidador());assertEquals("Nombre",legacy.getNombre());
        verify(em,never()).remove(any());verify(em,never()).persist(any());
    }
    @Test void comandoExigeModoOfflineYNoAplicaSiHayFormatosInesperados() throws Exception {
        var service=mock(TransicionCodigosDispositivo.class);var config=new DispositivoConfiguration();
        var env=new MockEnvironment().withProperty("diafacil.dispositivos.codigos.transicion","apply");
        assertThrows(IllegalStateException.class,()->config.transicionCodigos(service,env).run(null));verifyNoInteractions(service);
        env.withProperty("spring.main.web-application-type","none").withProperty("spring.jpa.hibernate.ddl-auto","none")
            .withProperty("diafacil.datos-base.enabled","false").withProperty("diafacil.dispositivos.enabled","false");
        when(service.auditar()).thenReturn(new TransicionCodigosDispositivo.Auditoria(1,0,1,0,true));
        assertThrows(IllegalStateException.class,()->config.transicionCodigos(service,env).run(null));verify(service,never()).aplicar();
    }
    private void unique(EntityManager em) {
        var session=mock(org.hibernate.Session.class);when(em.unwrap(org.hibernate.Session.class)).thenReturn(session);
        when(session.doReturningWork(any())).thenReturn(true);
    }
}
