package dia_facil.backend.service.ayuda;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.PerfilResumen;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SolicitudAyudaServiceTest {
 final EntityManager em=mock(EntityManager.class);final AccesoDispositivoService acceso=mock(AccesoDispositivoService.class);
 final Clock clock=Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"),ZoneId.of("America/Lima"));
 final SolicitudAyudaService service=new SolicitudAyudaService(em,acceso,clock);
 final AccesoDispositivoService.Identidad principal=new AccesoDispositivoService.Identidad(1L,12);
 final PerfilUsuario perfil=PerfilUsuario.crear(Cuidador.crear("test@example.invalid","Test"),"Grecia",null,"DF-TEST");
 final String clave="c3c155b2-f8b5-4dba-8cb1-47ab9c6df806";
 final List<SolicitudAyuda> solicitudes=new ArrayList<>(); final List<NotificacionCuidador> notificaciones=new ArrayList<>();
 @BeforeEach @SuppressWarnings("unchecked") void setup(){
  ReflectionTestUtils.setField(perfil,"idPerfilUsuario",12);
  when(acceso.perfilActivo(principal)).thenReturn(new PerfilResumen(12,"Grecia",null));
  when(em.find(PerfilUsuario.class,12,LockModeType.PESSIMISTIC_WRITE)).thenReturn(perfil);
  when(em.createQuery(startsWith("select s from SolicitudAyuda"),eq(SolicitudAyuda.class))).thenAnswer(call->{
   var q=mock(TypedQuery.class);var params=new HashMap<String,Object>();
   when(q.setParameter(anyString(),any())).thenAnswer(c->{params.put(c.getArgument(0),c.getArgument(1));return q;});
   when(q.setMaxResults(anyInt())).thenReturn(q);
   when(q.getResultStream()).thenAnswer(c->solicitudes.stream().filter(s->params.containsKey("clave")?s.getClaveOperacion().equals(params.get("clave")):true));return q;
  });
  doAnswer(call->{Object v=call.getArgument(0);if(v instanceof SolicitudAyuda s){ReflectionTestUtils.setField(s,"idSolicitudAyuda",solicitudes.size()+1);ReflectionTestUtils.setField(s,"fechaSolicitud",LocalDateTime.now(clock));solicitudes.add(s);}if(v instanceof NotificacionCuidador n)notificaciones.add(n);return null;}).when(em).persist(any());
 }
 @Test void generalEsAtomicaYReintentoDevuelveOriginalAntesDelCooldown(){
  var body=new SolicitudAyudaService.Peticion(clave,null,null,null);var r=service.crear(principal,body);
  assertEquals(r,service.crear(principal,body));assertEquals(1,solicitudes.size());assertEquals(1,notificaciones.size());assertNull(r.idActividad());assertNull(notificaciones.getFirst().getEjecucionActividad());assertEquals("Grecia necesita tu ayuda",notificaciones.getFirst().getMensaje());
  var orden=inOrder(em);orden.verify(em).find(PerfilUsuario.class,12,LockModeType.PESSIMISTIC_WRITE);orden.verify(em).refresh(perfil,LockModeType.PESSIMISTIC_WRITE);
 }
 @Test void cooldownYBordeExacto(){
  service.crear(principal,new SolicitudAyudaService.Peticion(clave,null,null,null));
  var otro=new SolicitudAyudaService.Peticion(UUID.randomUUID().toString(),null,null,null);
  var ex=assertThrows(RechazoAyuda.class,()->service.crear(principal,otro));assertEquals("AYUDA_PREMATURA",ex.codigo());assertEquals(60,ex.espera());assertEquals(1,notificaciones.size());
  ReflectionTestUtils.setField(solicitudes.getFirst(),"fechaSolicitud",LocalDateTime.now(clock).minusSeconds(60));service.crear(principal,otro);assertEquals(2,notificaciones.size());
 }
 @Test void consultaNoEscribeYNoEncontrada(){assertEquals("SOLICITUD_NO_ENCONTRADA",assertThrows(RechazoAyuda.class,()->service.consultar(principal,clave)).codigo());verify(em,never()).persist(any());}
 @Test void rechazaContextoParcialYAjenoSinEscribir(){
  assertThrows(RechazoAyuda.class,()->service.crear(principal,new SolicitudAyudaService.Peticion(clave,7,null,null)));
  assertEquals("CONTEXTO_AYUDA_NO_VALIDO",assertThrows(RechazoAyuda.class,()->service.crear(principal,new SolicitudAyudaService.Peticion(clave,7,8,9))).codigo());verify(em,never()).persist(any());
 }
 @Test @SuppressWarnings("unchecked") void contextualConservaPasoHistoricoSinProgresoArtificial(){
  var a=Actividad.crear(perfil,"Desayunar",LocalTime.of(7,0),null);ReflectionTestUtils.setField(a,"idActividad",7);
  var e=EjecucionActividad.iniciar(a,LocalDate.now(clock));ReflectionTestUtils.setField(e,"idEjecucionActividad",8);
  var paso=PasoActividad.crear(a,5,"Preparar pan",null);ReflectionTestUtils.setField(paso,"idPasoActividad",9);
  when(em.find(Actividad.class,7)).thenReturn(a);when(em.find(EjecucionActividad.class,8)).thenReturn(e);when(em.find(PasoActividad.class,9)).thenReturn(paso);
  var count=mock(TypedQuery.class);when(count.setParameter(anyString(),any())).thenReturn(count);when(count.getSingleResult()).thenReturn(1L);
  when(em.createQuery(startsWith("select count(p)"),eq(Long.class))).thenReturn(count);
  var pasos=mock(TypedQuery.class);when(pasos.setParameter(anyString(),any())).thenReturn(pasos);when(pasos.getResultList()).thenReturn(List.of(paso));
  when(em.createQuery(startsWith("select p from PasoActividad"),eq(PasoActividad.class))).thenReturn(pasos);
  var r=service.crear(principal,new SolicitudAyudaService.Peticion(clave,7,8,9));
  assertEquals(1,r.posicionPaso());assertEquals("Preparar pan",r.descripcionPaso());assertEquals(8,r.idEjecucion());assertNull(notificaciones.getFirst().getEjecucionActividad());assertSame(solicitudes.getFirst(),notificaciones.getFirst().getSolicitudAyuda());
  assertEquals(dia_facil.backend.persistence.enums.EstadoEjecucion.EN_PROGRESO,e.getEstado());verify(em,never()).persist(isA(ProgresoPaso.class));
  paso.setDescripcion("Texto nuevo");a.setNombre("Nombre nuevo");assertEquals("Preparar pan",solicitudes.getFirst().getDescripcionPaso());assertTrue(notificaciones.getFirst().getMensaje().endsWith("Desayunar"));
  when(count.getSingleResult()).thenReturn(0L);var ex=assertThrows(RechazoAyuda.class,()->service.crear(principal,new SolicitudAyudaService.Peticion(UUID.randomUUID().toString(),7,8,9)));assertEquals("CONTEXTO_AYUDA_NO_VALIDO",ex.codigo());assertEquals(1,solicitudes.size());
 }
 @Test void uuidEstrictoYCanonico(){assertEquals(clave,SolicitudAyudaService.clave(clave.toUpperCase()));for(String s:new String[]{"", "1-1-1-1-1", "invalida"})assertThrows(RechazoAyuda.class,()->SolicitudAyudaService.clave(s));assertThrows(RechazoAyuda.class,()->SolicitudAyudaService.clave(null));}
}
