package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoDispositivo;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.dispositivos.*;
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

/** Opt-in disposable existing schema, no DDL or application-local datasource. */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL",matches=".+")
class DispositivoMySqlTest {
    private <T> T proxy(T target,JpaTransactionManager manager) {
        if(target instanceof AccesoDispositivoService access) access.habilitar();
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();
    }
    @Test void vinculaSinDuplicarConcurrenciaRollbackYRenovarNoRevoca() throws Exception {
        String url=System.getenv("DIAFACIL_TEST_MYSQL_URL");assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds=new DriverManagerDataSource(url,System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try(var c=ds.getConnection()){assertTrue(c.getCatalog().startsWith("diafacil_test_"));}
        var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource",ds)
            .applySetting("hibernate.connection.handling_mode","DELAYED_ACQUISITION_AND_HOLD").applySetting("hibernate.hbm2ddl.auto","none").build();
        var sources=new MetadataSources(registry);PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
        try(var factory=sources.buildMetadata().buildSessionFactory()) {
            EntityManager em=SharedEntityManagerCreator.createSharedEntityManager(factory);var tm=new JpaTransactionManager(factory);var tx=new TransactionTemplate(tm);
            tx.executeWithoutResult(status -> EsquemaDispositivo.verificar(em));
            var gen=new GeneradorCodigoPerfil();String code=gen.generar();
            var ids=Objects.requireNonNull(tx.execute(s->{var owner=Cuidador.crear(UUID.randomUUID()+"@example.invalid","Test");em.persist(owner);
                var profile=PerfilUsuario.crear(owner,"Test",null,code);em.persist(profile);em.flush();return List.of(owner.getIdCuidador(),profile.getIdPerfilUsuario());}));
            var access=proxy(new AccesoDispositivoService(em),tm);var grants=new ArrayList<Long>();var pool=Executors.newFixedThreadPool(2);
            try {
                String token=access.preparar().credencialDispositivo();Long cred=access.autenticar(token).idCredencial();grants.add(cred);
                var start=new CountDownLatch(1);Callable<Integer> bind=()->{start.await(10,TimeUnit.SECONDS);try{access.vincular(cred,code,"Test");return 201;}catch(RechazoDispositivo ex){return ex.status();}};
                var first=pool.submit(bind);var second=pool.submit(bind);start.countDown();assertEquals(Set.of(201,409),Set.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));
                assertEquals("VINCULADO",access.consultar(cred).estado());
                String reconnect=access.preparar().credencialDispositivo();Long reconnectId=access.autenticar(reconnect).idCredencial();grants.add(reconnectId);
                var reuse=access.vincular(reconnectId,code,"Same installation",List.of(token));
                assertEquals(token,reuse.credencialDispositivo());assertEquals("PENDIENTE",access.consultar(reconnectId).estado());
                // Explicit retries, including after a lost response, preserve the original device and proof.
                assertEquals(token,access.vincular(reconnectId,code,"Retry",List.of(token)).credencialDispositivo());
                String token2=access.preparar().credencialDispositivo();Long cred2=access.autenticar(token2).idCredencial();grants.add(cred2);
                var failing=mock(EntityManager.class,org.mockito.AdditionalAnswers.delegatesTo(em));doThrow(new IllegalStateException("synthetic")).when(failing).flush();
                assertThrows(IllegalStateException.class,()->proxy(new AccesoDispositivoService(failing),tm).vincular(cred2,code,"Second"));
                assertEquals("PENDIENTE",access.consultar(cred2).estado());access.vincular(cred2,code,"Second");
                var repos=new JpaRepositoryFactory(em);var devices=repos.getRepository(DispositivoRepository.class);
                assertEquals(2,devices.countByPerfilUsuario_IdPerfilUsuarioAndEstado(ids.get(1),EstadoDispositivo.ACTIVO));
                var renewal=proxy(new VinculacionTransaccionalService(repos.getRepository(PerfilUsuarioRepository.class),devices,gen,em),tm);
                var renewed=renewal.renovar(ids.get(0),ids.get(1),code);assertEquals(2,renewed.dispositivosActivos());assertNotNull(access.autenticar(token).idPerfilUsuario());
                String token3=access.preparar().credencialDispositivo();Long cred3=access.autenticar(token3).idCredencial();grants.add(cred3);
                assertEquals(422,assertThrows(RechazoDispositivo.class,()->access.vincular(cred3,code,"Third")).status());
                access.vincular(cred3,renewed.codigoVinculacion(),"Third");assertEquals(3,devices.countByPerfilUsuario_IdPerfilUsuarioAndEstado(ids.get(1),EstadoDispositivo.ACTIVO));
                assertEquals(403,assertThrows(RechazoDispositivo.class,()->access.salir(cred)).status());
                tx.executeWithoutResult(txStatus->{em.find(CredencialDispositivo.class,cred).getDispositivo().setEstado(EstadoDispositivo.DESVINCULADO);});assertThrows(RechazoDispositivo.class,()->access.autenticar(token));
                assertNotNull(access.autenticar(token2).idPerfilUsuario());assertEquals(2,devices.countByPerfilUsuario_IdPerfilUsuarioAndEstado(ids.get(1),EstadoDispositivo.ACTIVO));
            } finally {
                pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));
                tx.executeWithoutResult(s->{for(Long id:grants)em.createQuery("delete from CredencialDispositivo c where c.idCredencialDispositivo=:id").setParameter("id",id).executeUpdate();
                    em.createQuery("delete from Cuidador c where c.idCuidador=:id").setParameter("id",ids.get(0)).executeUpdate();});
            }
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }
}
