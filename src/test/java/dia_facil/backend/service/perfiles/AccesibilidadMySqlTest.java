package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
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

/** Explicit disposable schema only. No DDL and no development credentials. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_MYSQL_URL", matches = ".+")
class AccesibilidadMySqlTest {
    @Test void actualizacionesConcurrentesDeCamposDistintosNoSePisanNiDuplicanConfiguracionHistorica() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_MYSQL_USER"), System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) { assertTrue(c.getCatalog().startsWith("diafacil_test_")); }
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
            .applySetting("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD").applySetting("hibernate.hbm2ddl.auto", "none").build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(Cuidador.class).addAnnotatedClass(PerfilUsuario.class)
                .addAnnotatedClass(ConfiguracionPerfil.class).buildMetadata().buildSessionFactory()) {
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            var manager = new JpaTransactionManager(factory); var tx = new TransactionTemplate(manager);
            var proxy = new ProxyFactory(new AccesibilidadService(em)); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var service = (AccesibilidadService) proxy.getProxy();
            var ids = Objects.requireNonNull(tx.execute(status -> {
                var owner = Cuidador.crear(UUID.randomUUID() + "@example.invalid", "Test"); em.persist(owner);
                var profile = PerfilUsuario.crear(owner, "Test", null, new GeneradorCodigoPerfil().generar()); em.persist(profile); em.flush();
                return new int[] {owner.getIdCuidador(), profile.getIdPerfilUsuario()};
            }));
            var pool = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1);
                var a = pool.submit(() -> { start.await(); return service.actualizar(ids[0], ids[1], Map.of("altoContraste", true)); });
                var b = pool.submit(() -> { start.await(); return service.actualizar(ids[0], ids[1], Map.of("lecturaVoz", false)); });
                start.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
                var result = service.consultar(ids[0], ids[1]); assertTrue(result.altoContraste()); assertFalse(result.lecturaVoz());
                tx.executeWithoutResult(status -> {
                    assertEquals(1L, em.createQuery("select count(c) from ConfiguracionPerfil c where c.idPerfilUsuario = :id", Long.class).setParameter("id", ids[1]).getSingleResult());
                    assertEquals(10, em.find(ConfiguracionPerfil.class, ids[1]).getRecordatorioMinutos());
                });
            } finally {
                pool.shutdownNow(); assertTrue(pool.awaitTermination(25, TimeUnit.SECONDS));
                tx.executeWithoutResult(status -> {
                    em.createQuery("delete from ConfiguracionPerfil where idPerfilUsuario = :id").setParameter("id", ids[1]).executeUpdate();
                    em.createQuery("delete from PerfilUsuario where idPerfilUsuario = :id").setParameter("id", ids[1]).executeUpdate();
                    em.createQuery("delete from Cuidador where idCuidador = :id").setParameter("id", ids[0]).executeUpdate();
                });
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
