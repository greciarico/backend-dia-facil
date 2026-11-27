package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoActividad;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.actividades.*;
import dia_facil.backend.service.perfiles.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit disposable schema only, no DDL, no local configuration, synthetic provider metadata. */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL", matches=".+")
class CreacionActividadMySqlTest {
    @Test void rollbackRealYDosCreacionesCompartenUnaSolaReferenciaExterna() throws Exception {
        String url=System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds=new DriverManagerDataSource(url,System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try(var c=ds.getConnection()){assertTrue(c.getCatalog().startsWith("diafacil_test_"));}
        var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource",ds)
            .applySetting("hibernate.connection.handling_mode","DELAYED_ACQUISITION_AND_HOLD").applySetting("hibernate.hbm2ddl.auto","none").build();
        var sources=new MetadataSources(registry); PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
        try(var factory=sources.buildMetadata().buildSessionFactory()) {
            EntityManager em=SharedEntityManagerCreator.createSharedEntityManager(factory);
            var manager=new JpaTransactionManager(factory); var tx=new TransactionTemplate(manager);
            var repos=new JpaRepositoryFactory(em); var profiles=repos.getRepository(PerfilUsuarioRepository.class);
            var actividades=repos.getRepository(ActividadRepository.class);
            var icons=repos.getRepository(IconoActividadRepository.class); var pictos=repos.getRepository(PictogramaRepository.class);
            String code="test-"+UUID.randomUUID(); int arasaac=1_500_000_000+new Random().nextInt(100_000_000);
            assertTrue(pictos.findByIdArasaacIn(List.of(arasaac,arasaac+1)).isEmpty(),"Synthetic identities must not exist");
            var ids=Objects.requireNonNull(tx.execute(status->{
                var owner=Cuidador.crear(UUID.randomUUID()+"@example.invalid","Test"); em.persist(owner);
                var profile=PerfilUsuario.crear(owner,"Test",null,"DF-"+String.format("%06d",new Random().nextInt(1_000_000))); em.persist(profile);
                var icon=IconoActividad.crear(code,"Test"); em.persist(icon); em.flush();
                return List.of(owner.getIdCuidador(),profile.getIdPerfilUsuario(),icon.getIdIconoActividad());
            }));
            var pool=Executors.newFixedThreadPool(2);
            try {
                var data=new CrearActividad("Test","07:00",code,List.of(new CrearActividad.Paso("Uno",arasaac),new CrearActividad.Paso("Dos",null))).validado();
                var failure=mock(EntityManager.class,org.mockito.AdditionalAnswers.delegatesTo(em));
                doAnswer(call->{if(call.getArgument(0) instanceof PasoActividad p && p.getOrden()==2)throw new IllegalStateException("injected"); em.persist(call.getArgument(0)); return null;}).when(failure).persist(any());
                var failed=proxied(new CreacionActividadTransaccionalService(profiles,icons,pictos,failure,actividades,mock(RegistroSeguimiento.class)),manager);
                assertThrows(IllegalStateException.class,()->failed.crear(ids.get(0),ids.get(1),data,Map.of(arasaac,PictogramaArasaac.de(arasaac,"Test"))));
                long afterRollback=tx.execute(status->em.createQuery("select count(a) from Actividad a where a.perfilUsuario.idPerfilUsuario=:p",Long.class).setParameter("p",ids.get(1)).getSingleResult());
                assertEquals(0L,afterRollback);
                assertTrue(pictos.findByIdArasaacIn(List.of(arasaac)).isEmpty());
                var remote=mock(PictogramaArasaacService.class); when(remote.resolver(arasaac)).thenReturn(PictogramaArasaac.de(arasaac,"Test"));
                var transacciones = proxied(new CreacionActividadTransaccionalService(profiles,icons,pictos,em,actividades,mock(RegistroSeguimiento.class)),manager);
                var service=new CrearActividadService(new PerfilUsuarioService(profiles),icons,pictos,remote,transacciones);
                var start=new CountDownLatch(1);
                Callable<ActividadResumen> create=()->{assertTrue(start.await(10,TimeUnit.SECONDS));return service.crear(ids.get(0),ids.get(1),data);};
                var a=pool.submit(create);var b=pool.submit(create);start.countDown();
                var primera = a.get(30,TimeUnit.SECONDS);
                var segunda = b.get(30,TimeUnit.SECONDS);
                assertNotEquals(primera.idActividad(),segunda.idActividad());
                assertEquals(1,pictos.findByIdArasaacIn(List.of(arasaac)).size());
                long activities=tx.execute(status->em.createQuery("select count(a) from Actividad a where a.perfilUsuario.idPerfilUsuario=:p",Long.class).setParameter("p",ids.get(1)).getSingleResult());
                long steps=tx.execute(status->em.createQuery("select count(p) from PasoActividad p where p.actividad.perfilUsuario.idPerfilUsuario=:p",Long.class).setParameter("p",ids.get(1)).getSingleResult());
                assertEquals(2L,activities);assertEquals(4L,steps);
                for (int i = 0; i < 7; i++) service.crear(ids.get(0), ids.get(1), data);
                tx.executeWithoutResult(status -> {
                    var perfil = em.find(PerfilUsuario.class, ids.get(1));
                    var icono = em.find(IconoActividad.class, ids.get(2));
                    var inactiva = Actividad.crear(perfil, "Inactiva", java.time.LocalTime.NOON, icono);
                    inactiva.setEstado(EstadoActividad.INACTIVA);
                    em.persist(inactiva);
                });
                var ready = new CountDownLatch(2);
                var race = new CountDownLatch(1);
                Callable<String> compete = () -> {
                    ready.countDown();
                    assertTrue(race.await(10, TimeUnit.SECONDS));
                    try { create.call(); return "CREADA"; }
                    catch (RechazoActividad error) { return error.codigo(); }
                };
                var first = pool.submit(compete);
                var second = pool.submit(compete);
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                race.countDown();
                assertEquals(Set.of("CREADA", "LIMITE_ACTIVIDADES_ALCANZADO"),
                    new HashSet<>(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))));
                long activas = tx.execute(status ->
                    actividades.countByPerfilUsuario_IdPerfilUsuarioAndEstado(ids.get(1), EstadoActividad.ACTIVA));
                assertEquals(10L, activas);
                var edicion = new EdicionActividadService(em, profiles, icons, service, transacciones, manager,
                    java.time.Clock.system(java.time.ZoneId.of("America/Lima")), mock(RegistroSeguimiento.class));
                var empezarEdicion = new CountDownLatch(1);
                java.util.function.Function<String, Callable<String>> editar = nombre -> () -> {
                    assertTrue(empezarEdicion.await(10, TimeUnit.SECONDS));
                    var entrada = new EdicionActividadService.Entrada(1, nombre, "09:00", code,
                        List.of(new EdicionActividadService.PasoEntrada("Paso editado", null, null)));
                    try { edicion.editar(ids.get(0), ids.get(1), primera.idActividad(), entrada); return "ACTUALIZADA"; }
                    catch (RechazoActividad error) { return error.codigo(); }
                };
                var editarA = pool.submit(editar.apply("Edición A"));
                var editarB = pool.submit(editar.apply("Edición B"));
                empezarEdicion.countDown();
                assertEquals(Set.of("ACTUALIZADA", "ACTIVIDAD_MODIFICADA"),
                    new HashSet<>(List.of(editarA.get(30, TimeUnit.SECONDS), editarB.get(30, TimeUnit.SECONDS))));
                var vigente = tx.execute(status -> edicion.consultar(ids.get(0), ids.get(1), primera.idActividad()));
                assertEquals(2, vigente.revision());
                assertEquals("09:00", vigente.hora());
                assertEquals(1, vigente.pasos().size());
                var borrada = tx.execute(status -> edicion.eliminar(ids.get(0), ids.get(1), primera.idActividad(), 2));
                assertEquals("BORRADA", borrada.resultado());
                tx.executeWithoutResult(status -> {
                    assertNull(em.find(Actividad.class, primera.idActividad()));
                    assertEquals(0L, em.createQuery("select count(v) from VersionActividad v where v.actividad.idActividad=:id", Long.class)
                        .setParameter("id", primera.idActividad()).getSingleResult());
                    assertEquals(0L, em.createQuery("select count(p) from PasoActividad p where p.actividad.idActividad=:id", Long.class)
                        .setParameter("id", primera.idActividad()).getSingleResult());
                    assertNotNull(em.find(IconoActividad.class, ids.get(2)));
                    assertEquals(1, pictos.findByIdArasaacIn(List.of(arasaac)).size());
                    var actividad = em.find(Actividad.class, segunda.idActividad());
                    em.persist(EjecucionActividad.iniciar(actividad, java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"))));
                });
                var retirada = tx.execute(status -> edicion.eliminar(ids.get(0), ids.get(1), segunda.idActividad(), 1));
                assertEquals("RETIRADA", retirada.resultado());
                assertEquals("EN_PROGRESO", retirada.estadoHoy());
                long restantes = tx.execute(status ->
                    actividades.countByPerfilUsuario_IdPerfilUsuarioAndEstado(ids.get(1), EstadoActividad.ACTIVA));
                assertEquals(8L, restantes);
            } finally {
                pool.shutdownNow(); assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));
                tx.executeWithoutResult(status->{
                    em.createQuery("delete from Cuidador c where c.idCuidador=:id").setParameter("id",ids.get(0)).executeUpdate();
                    em.createQuery("delete from IconoActividad i where i.idIconoActividad=:id").setParameter("id",ids.get(2)).executeUpdate();
                    em.createQuery("delete from Pictograma p where p.idArasaac=:id").setParameter("id",arasaac).executeUpdate();
                });
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
    private CreacionActividadTransaccionalService proxied(CreacionActividadTransaccionalService target,JpaTransactionManager manager){
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (CreacionActividadTransaccionalService)proxy.getProxy();
    }
}
