package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.*;
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
                var failed=proxied(new CreacionActividadTransaccionalService(profiles,icons,pictos,failure),manager);
                assertThrows(IllegalStateException.class,()->failed.crear(ids.get(0),ids.get(1),data,Map.of(arasaac,PictogramaArasaac.de(arasaac,"Test"))));
                long afterRollback=tx.execute(status->em.createQuery("select count(a) from Actividad a where a.perfilUsuario.idPerfilUsuario=:p",Long.class).setParameter("p",ids.get(1)).getSingleResult());
                assertEquals(0L,afterRollback);
                assertTrue(pictos.findByIdArasaacIn(List.of(arasaac)).isEmpty());
                var remote=mock(PictogramaArasaacService.class); when(remote.resolver(arasaac)).thenReturn(PictogramaArasaac.de(arasaac,"Test"));
                var service=new CrearActividadService(new PerfilUsuarioService(profiles),icons,pictos,remote,
                    proxied(new CreacionActividadTransaccionalService(profiles,icons,pictos,em),manager));
                var start=new CountDownLatch(1);
                Callable<ActividadResumen> create=()->{assertTrue(start.await(10,TimeUnit.SECONDS));return service.crear(ids.get(0),ids.get(1),data);};
                var a=pool.submit(create);var b=pool.submit(create);start.countDown();
                assertNotEquals(a.get(30,TimeUnit.SECONDS).idActividad(),b.get(30,TimeUnit.SECONDS).idActividad());
                assertEquals(1,pictos.findByIdArasaacIn(List.of(arasaac)).size());
                long activities=tx.execute(status->em.createQuery("select count(a) from Actividad a where a.perfilUsuario.idPerfilUsuario=:p",Long.class).setParameter("p",ids.get(1)).getSingleResult());
                long steps=tx.execute(status->em.createQuery("select count(p) from PasoActividad p where p.actividad.perfilUsuario.idPerfilUsuario=:p",Long.class).setParameter("p",ids.get(1)).getSingleResult());
                assertEquals(2L,activities);assertEquals(4L,steps);
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
