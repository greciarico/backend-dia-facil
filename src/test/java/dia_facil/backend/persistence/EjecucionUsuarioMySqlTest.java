package dia_facil.backend.persistence;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.actividades.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Explicit disposable schema only. No DDL; deletes only its own fixture owner. */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL",matches=".+")
class EjecucionUsuarioMySqlTest {
 EjecucionUsuarioService proxy(EjecucionUsuarioService service,JpaTransactionManager manager){var p=new ProxyFactory(service);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (EjecucionUsuarioService)p.getProxy();}
 @Test void inicioYAvanceConcurrentesSonUnicosYRollbackNoDejaProgresoParcial() throws Exception {
  String url=System.getenv("DIAFACIL_TEST_MYSQL_URL");assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
  var ds=new DriverManagerDataSource(url,System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));try(var c=ds.getConnection()){assertTrue(c.getCatalog().startsWith("diafacil_test_"));}
  var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource",ds).applySetting("hibernate.hbm2ddl.auto","none").build();
  var sources=new MetadataSources(registry);PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
  try(var factory=sources.buildMetadata().buildSessionFactory()){
   EntityManager em=SharedEntityManagerCreator.createSharedEntityManager(factory);var tm=new JpaTransactionManager(factory);var tx=new TransactionTemplate(tm);
   var ids=Objects.requireNonNull(tx.execute(s->{var owner=Cuidador.crear(UUID.randomUUID()+"@example.invalid","Test");em.persist(owner);
    var perfil=PerfilUsuario.crear(owner,"Test",null,new GeneradorCodigoPerfil().generar());em.persist(perfil);var a=Actividad.crear(perfil,"Test",LocalTime.of(7,0),null);em.persist(a);
    var out=new ArrayList<Integer>(List.of(owner.getIdCuidador(),perfil.getIdPerfilUsuario(),a.getIdActividad()));
    for(int i=1;i<=3;i++){var p=PasoActividad.crear(a,i,"Step "+i,null);em.persist(p);out.add(p.getIdPasoActividad());}em.flush();return out;}));
   var access=mock(AccesoDispositivoService.class);var principal=new AccesoDispositivoService.Identidad(Long.MAX_VALUE,ids.get(1));
   when(access.perfilActivo(principal)).thenReturn(new PerfilResumen(ids.get(1),"Test",null));
   var clock=Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"),ZoneId.of("America/Lima"));var date=LocalDate.of(2026,10,24);
   var service=proxy(new EjecucionUsuarioService(em,access,clock),tm);var pool=Executors.newFixedThreadPool(2);
   try{
    var start=new CountDownLatch(1);Callable<EjecucionUsuarioService.Guia> init=()->{start.await(10,TimeUnit.SECONDS);return service.iniciar(principal,ids.get(2),date);};
    var a=pool.submit(init);var b=pool.submit(init);start.countDown();int execution=a.get(30,TimeUnit.SECONDS).idEjecucion();assertEquals(execution,b.get(30,TimeUnit.SECONDS).idEjecucion());
    var advanceStart=new CountDownLatch(1);Callable<EjecucionUsuarioService.Guia> advance=()->{advanceStart.await(10,TimeUnit.SECONDS);return service.avanzar(principal,ids.get(2),execution,ids.get(3));};
    var c=pool.submit(advance);var d=pool.submit(advance);advanceStart.countDown();c.get(30,TimeUnit.SECONDS);d.get(30,TimeUnit.SECONDS);
    var state=service.consultar(principal,ids.get(2),execution);assertTrue(state.pasos().get(1).alcanzado());assertFalse(state.pasos().get(2).alcanzado());
    var failing=mock(EntityManager.class,org.mockito.AdditionalAnswers.delegatesTo(em));doAnswer(call->{em.flush();throw new IllegalStateException("injected failure after flush");}).when(failing).flush();
    assertThrows(IllegalStateException.class,()->proxy(new EjecucionUsuarioService(failing,access,clock),tm).avanzar(principal,ids.get(2),execution,ids.get(4)));
    state=service.consultar(principal,ids.get(2),execution);assertFalse(state.pasos().get(1).completado());assertFalse(state.pasos().get(2).alcanzado());assertFalse(state.actividad().guiaInicialCompletada());
    service.avanzar(principal,ids.get(2),execution,ids.get(4));var done=service.finalizarGuiada(principal,ids.get(2),execution,ids.get(5));assertTrue(done.actividad().guiaInicialCompletada());
    assertEquals("GUIADA",service.finalizarDirecta(principal,ids.get(2),date).modoFinalizacion());
    var tomorrow=proxy(new EjecucionUsuarioService(em,access,Clock.offset(clock,Duration.ofDays(1))),tm);
    var direct=tomorrow.finalizarDirecta(principal,ids.get(2),date.plusDays(1));assertNotEquals(execution,direct.idEjecucion());assertEquals("DIRECTA",direct.modoFinalizacion());
    assertTrue(tomorrow.consultar(principal,ids.get(2),direct.idEjecucion()).pasos().stream().noneMatch(EjecucionUsuarioService.Paso::alcanzado));
    long count=tx.execute(s->em.createQuery("select count(e) from EjecucionActividad e where e.actividad.idActividad=:id",Long.class).setParameter("id",ids.get(2)).getSingleResult());assertEquals(2,count);
   }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));tx.executeWithoutResult(s->em.createQuery("delete from Cuidador c where c.idCuidador=:id").setParameter("id",ids.getFirst()).executeUpdate());}
  }finally{StandardServiceRegistryBuilder.destroy(registry);}
 }
}
