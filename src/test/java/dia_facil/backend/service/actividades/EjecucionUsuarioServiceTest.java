package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.PerfilResumen;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EjecucionUsuarioServiceTest {
    final EntityManager em=mock(EntityManager.class);
    final AccesoDispositivoService acceso=mock(AccesoDispositivoService.class);
    final PerfilUsuario perfil=PerfilUsuario.crear(Cuidador.crear("test@example.invalid","Test"),"Persona",null,"DF-7KQ9W6H2M8V3X5RA");
    final Actividad actividad=Actividad.crear(perfil,"Actividad",LocalTime.of(7,0),null);
    final List<PasoActividad> pasos=new ArrayList<>();
    final List<EjecucionActividad> ejecuciones=new ArrayList<>();
    final List<ProgresoPaso> progreso=new ArrayList<>();
    final AccesoDispositivoService.Identidad principal=new AccesoDispositivoService.Identidad(1L,999);
    final LocalDate fecha=LocalDate.of(2026,10,24);
    final MutableClock clock=new MutableClock();
    final EjecucionUsuarioService service=new EjecucionUsuarioService(em,acceso,clock);
    static class MutableClock extends Clock {
        Instant instant=Instant.parse("2026-10-24T12:00:00Z");
        public ZoneId getZone(){return ZoneId.of("America/Lima");} public Clock withZone(ZoneId z){return Clock.fixed(instant,z);} public Instant instant(){return instant;}
    }
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        ReflectionTestUtils.setField(perfil,"idPerfilUsuario",12);ReflectionTestUtils.setField(actividad,"idActividad",7);
        when(acceso.perfilActivo(principal)).thenAnswer(call->{if(perfil.getEstado()!=EstadoPerfil.ACTIVO)throw RechazoDispositivo.invalida();return new PerfilResumen(12,"Persona",null);});
        when(em.find(PerfilUsuario.class,12,LockModeType.PESSIMISTIC_WRITE)).thenReturn(perfil);
        when(em.find(Actividad.class,7)).thenReturn(actividad);
        when(em.find(eq(EjecucionActividad.class),anyInt())).thenAnswer(call->ejecuciones.stream().filter(e->e.getIdEjecucionActividad().equals(call.getArgument(1))).findFirst().orElse(null));
        for(int i=1;i<=5;i++){var p=PasoActividad.crear(actividad,i,"Paso "+i,null);ReflectionTestUtils.setField(p,"idPasoActividad",i);pasos.add(p);}
        when(em.createQuery(startsWith("select e from EjecucionActividad"),eq(EjecucionActividad.class))).thenAnswer(call->{
            var q=mock(TypedQuery.class);var params=new HashMap<String,Object>();
            when(q.setParameter(anyString(),any())).thenAnswer(c->{params.put(c.getArgument(0),c.getArgument(1));return q;});when(q.setLockMode(any())).thenReturn(q);
            when(q.getResultStream()).thenAnswer(c->ejecuciones.stream().filter(e->e.getFecha().equals(params.get("fecha"))&&e.getActividad().getIdActividad().equals(params.get("actividad"))));return q;
        });
        when(em.createQuery(startsWith("select p from PasoActividad"),eq(PasoActividad.class))).thenAnswer(call->{var q=mock(TypedQuery.class);when(q.setParameter(anyString(),any())).thenReturn(q);when(q.getResultList()).thenAnswer(c->pasos.stream().filter(p->p.getActivo()).toList());return q;});
        when(em.createQuery(startsWith("select p from ProgresoPaso"),eq(ProgresoPaso.class))).thenAnswer(call->{var q=mock(TypedQuery.class);var params=new HashMap<String,Object>();
            when(q.setParameter(anyString(),any())).thenAnswer(c->{params.put(c.getArgument(0),c.getArgument(1));return q;});when(q.getResultList()).thenAnswer(c->progreso.stream().filter(p->p.getEjecucionActividad().getIdEjecucionActividad().equals(params.get("ejecucion"))).toList());return q;});
        doAnswer(call->{Object value=call.getArgument(0);if(value instanceof EjecucionActividad e){ReflectionTestUtils.setField(e,"idEjecucionActividad",ejecuciones.size()+1);ejecuciones.add(e);}if(value instanceof ProgresoPaso p){ReflectionTestUtils.setField(p,"idProgresoPaso",progreso.size()+1);progreso.add(p);}return null;}).when(em).persist(any());
    }
    void start(){service.iniciar(principal,7,fecha);}
    @Test void consultaNoCreaYInicioRepetidoSoloAlcanzaPrimero() {
        var pending=service.consultar(principal,7,null);assertEquals("PENDIENTE",pending.estado());assertNull(pending.idEjecucion());assertEquals(5,pending.pasos().size());verify(em,never()).persist(any());
        start();var result=service.iniciar(principal,7,fecha);assertEquals(1,ejecuciones.size());assertEquals(1,progreso.size());assertFalse(progreso.getFirst().getCompletado());assertNull(progreso.getFirst().getFechaCompletado());
        assertEquals("EN_PROGRESO",result.estado());assertTrue(result.pasos().getFirst().alcanzado());assertFalse(actividad.getGuiaInicialCompletada());
        var order=inOrder(em);order.verify(em).find(PerfilUsuario.class,12,LockModeType.PESSIMISTIC_WRITE);order.verify(em).refresh(perfil,LockModeType.PESSIMISTIC_WRITE);order.verify(em).refresh(actividad,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void primeraGuiaNoPermiteSaltosDirectaNiCierreTempranoYFinalizaAtomicoAlUltimo() {
        start();assertEquals("RECORRIDO_NO_PERMITIDO",assertThrows(RechazoGuia.class,()->service.avanzar(principal,7,1,4)).codigo());
        assertEquals("GUIA_INICIAL_REQUERIDA",assertThrows(RechazoGuia.class,()->service.finalizarGuiada(principal,7,1,1)).codigo());
        assertEquals("GUIA_INICIAL_REQUERIDA",assertThrows(RechazoGuia.class,()->service.finalizarDirecta(principal,7,fecha)).codigo());
        service.avanzar(principal,7,1,1);service.avanzar(principal,7,1,1);assertEquals(2,progreso.size());assertFalse(progreso.get(1).getCompletado());
        for(int i=2;i<=4;i++)service.avanzar(principal,7,1,i);
        assertFalse(actividad.getGuiaInicialCompletada());assertNull(ejecuciones.getFirst().getFechaFin());
        assertThrows(RechazoGuia.class,()->service.finalizarGuiada(principal,7,1,1));
        var result=service.finalizarGuiada(principal,7,1,5);assertEquals("COMPLETADA",result.estado());assertEquals("GUIADA",result.modoFinalizacion());
        assertTrue(actividad.getGuiaInicialCompletada());assertEquals(5,progreso.size());assertTrue(progreso.stream().allMatch(p->p.getCompletado()&&p.getFechaCompletado()!=null));
        assertEquals(result,service.finalizarGuiada(principal,7,1,5));assertEquals("GUIADA",service.finalizarDirecta(principal,7,fecha).modoFinalizacion());
    }
    @Test void cierreAnticipadoPosteriorEsGuiadaYNoInventaProgreso() {
        actividad.setGuiaInicialCompletada(true);start();service.avanzar(principal,7,1,1);
        var result=service.finalizarGuiada(principal,7,1,2);assertEquals("GUIADA",result.modoFinalizacion());assertEquals(2,progreso.size());
        assertTrue(progreso.getFirst().getCompletado());assertFalse(progreso.get(1).getCompletado());assertNull(progreso.get(1).getFechaCompletado());
    }
    @Test void directaSinGuiaNoCreaProgresoYConGuiaPreviaConservaLoAlcanzado() {
        actividad.setGuiaInicialCompletada(true);var result=service.finalizarDirecta(principal,7,fecha);assertEquals("DIRECTA",result.modoFinalizacion());assertTrue(progreso.isEmpty());
        assertEquals(result,service.finalizarDirecta(principal,7,fecha));assertEquals(1,ejecuciones.size());
        clock.instant=clock.instant.plus(Duration.ofDays(1));var tomorrow=fecha.plusDays(1);service.iniciar(principal,7,tomorrow);
        assertEquals(1,progreso.size());service.finalizarDirecta(principal,7,tomorrow);assertEquals(1,progreso.size());assertFalse(progreso.getFirst().getCompletado());
    }
    @Test void actividadDeUnSoloPasoFinalizaSinAvanceArtificial() {
        pasos.subList(1,pasos.size()).clear();start();assertThrows(RechazoGuia.class,()->service.avanzar(principal,7,1,1));
        service.finalizarGuiada(principal,7,1,1);assertTrue(progreso.getFirst().getCompletado());assertTrue(actividad.getGuiaInicialCompletada());
    }
    @Test void medianocheBloqueaEscriturasAnterioresPeroPermiteRecuperarResultadoExacto() {
        start();service.avanzar(principal,7,1,1);clock.instant=Instant.parse("2026-10-25T05:00:00Z");
        assertEquals("DIA_AGENDA_CAMBIADO",assertThrows(RechazoGuia.class,()->service.avanzar(principal,7,1,2)).codigo());
        assertThrows(RechazoGuia.class,()->service.finalizarGuiada(principal,7,1,2));assertThrows(RechazoGuia.class,()->service.iniciar(principal,7,fecha));
        assertEquals(1,ejecuciones.size());assertEquals(2,progreso.size());assertFalse(service.consultar(principal,7,1).vigente());assertEquals("PENDIENTE",service.consultar(principal,7,null).estado());
        assertFalse(actividad.getGuiaInicialCompletada());
    }
    @Test void ownershipEstadosYPasosInconsistentesNoSeReparan() {
        var ajeno=PerfilUsuario.crear(Cuidador.crear("other@example.invalid","Other"),"Otra",null,"DF-7KQ9W6H2M8V3X5R2");ReflectionTestUtils.setField(ajeno,"idPerfilUsuario",99);
        actividad.setPerfilUsuario(ajeno);assertThrows(RechazoActividad.class,()->service.iniciar(principal,7,fecha));verify(em,never()).persist(any());
        actividad.setPerfilUsuario(perfil);actividad.setEstado(EstadoActividad.INACTIVA);assertThrows(RechazoActividad.class,()->service.consultar(principal,7,null));
        actividad.setEstado(EstadoActividad.ACTIVA);pasos.get(1).setOrden(1);assertThrows(RechazoGuia.class,()->service.iniciar(principal,7,fecha));assertTrue(ejecuciones.isEmpty());
        pasos.get(1).setOrden(2);perfil.setEstado(EstadoPerfil.INACTIVO);assertThrows(RechazoDispositivo.class,()->service.consultar(principal,7,null));
    }
    @Test void ejecucionAjenaNoEsAutoridadYNoSePuedeAccederPorId() {
        var otra=Actividad.crear(perfil,"Otra",LocalTime.NOON,null);ReflectionTestUtils.setField(otra,"idActividad",8);
        var e=EjecucionActividad.iniciar(otra,fecha);em.persist(e);
        assertEquals("EJECUCION_NO_ENCONTRADA",assertThrows(RechazoGuia.class,()->service.avanzar(principal,7,1,1)).codigo());
    }
}
