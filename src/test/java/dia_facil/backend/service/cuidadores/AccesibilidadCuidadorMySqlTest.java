package dia_facil.backend.service.cuidadores;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.TamanoTexto;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in disposable schema only, no DDL and no development credentials. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_MYSQL_URL", matches = ".+")
class AccesibilidadCuidadorMySqlTest {
    @Test void primeraFilaConcurrenteConservaAmbosCambiosYSinDuplicados() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_MYSQL_USER"), System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) { assertTrue(c.getCatalog().startsWith("diafacil_test_")); }
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
            .applySetting("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD").applySetting("hibernate.hbm2ddl.auto", "none").build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(Cuidador.class).addAnnotatedClass(ConfiguracionCuidador.class).buildMetadata().buildSessionFactory()) {
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            var manager = new JpaTransactionManager(factory); var tx = new TransactionTemplate(manager);
            var proxy = new ProxyFactory(new AccesibilidadCuidadorService(em)); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var service = (AccesibilidadCuidadorService) proxy.getProxy();
            var id = Objects.requireNonNull(tx.execute(status -> {
                var c = Cuidador.crear(UUID.randomUUID() + "@example.invalid", "Test"); em.persist(c); em.flush(); return c.getIdCuidador();
            }));
            var pool = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1);
                var a = pool.submit(() -> { start.await(); return service.actualizar(id, Map.of("altoContraste", true)); });
                var b = pool.submit(() -> { start.await(); return service.actualizar(id, Map.of("tamanoTexto", "GRANDE")); });
                start.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
                var result = service.consultar(id); assertTrue(result.altoContraste()); assertEquals(TamanoTexto.GRANDE, result.tamanoTexto());
                tx.executeWithoutResult(status -> assertEquals(1L, em.createQuery("select count(c) from ConfiguracionCuidador c where c.idCuidador = :id", Long.class).setParameter("id", id).getSingleResult()));
            } finally {
                pool.shutdownNow(); assertTrue(pool.awaitTermination(25, TimeUnit.SECONDS));
                tx.executeWithoutResult(status -> {
                    em.createQuery("delete from ConfiguracionCuidador where idCuidador = :id").setParameter("id", id).executeUpdate();
                    em.createQuery("delete from Cuidador where idCuidador = :id").setParameter("id", id).executeUpdate();
                });
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
