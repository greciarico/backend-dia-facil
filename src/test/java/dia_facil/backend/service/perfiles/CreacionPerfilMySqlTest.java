package dia_facil.backend.service.perfiles;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.*;
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
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only. Existing schema, dedicated database, no DDL or development credentials. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_MYSQL_URL", matches = ".+")
class CreacionPerfilMySqlTest {
    @Test void dosCreacionesDesdeTresActivosProducenUnSoloCuartoPerfil() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
        // Reject before any JDBC call, including attempts to override a database in query parameters.
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"), "Se requiere una base exclusiva diafacil_test_*");
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="), "No se admiten cambios de catálogo por parámetros");
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_MYSQL_USER"), System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) { assertTrue(c.getCatalog().startsWith("diafacil_test_")); }
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
            .applySetting("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
            .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(Cuidador.class).addAnnotatedClass(PerfilUsuario.class)
                .addAnnotatedClass(ConfiguracionPerfil.class).buildMetadata().buildSessionFactory()) {
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            var repositories = new JpaRepositoryFactory(em);
            var caregivers = repositories.getRepository(CuidadorRepository.class);
            var profiles = repositories.getRepository(PerfilUsuarioRepository.class);
            var manager = new JpaTransactionManager(factory);
            var tx = new TransactionTemplate(manager);
            var codes = new GeneradorCodigoPerfil();
            var proxy = new ProxyFactory(new CreacionPerfilTransaccionalService(caregivers, profiles, em, codes));
            proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var service = new CrearPerfilService((CreacionPerfilTransaccionalService) proxy.getProxy());
            int owner = Objects.requireNonNull(tx.execute(status -> {
                var caregiver = Cuidador.crear(UUID.randomUUID() + "@example.invalid", "Prueba de concurrencia");
                em.persist(caregiver); em.flush(); return caregiver.getIdCuidador();
            }));
            var executor = Executors.newFixedThreadPool(2);
            try {
                for (int i = 0; i < 3; i++) service.crear(owner, "Inicial " + i, null);
                tx.executeWithoutResult(status -> {
                    var inactive = PerfilUsuario.crear(em.getReference(Cuidador.class, owner), "Inactivo", null, codes.generar());
                    inactive.setEstado(EstadoPerfil.INACTIVO); em.persist(inactive); em.persist(ConfiguracionPerfil.inicialPara(inactive)); em.flush();
                });
                var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
                Callable<Integer> attempt = () -> {
                    ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                    try { service.crear(owner, "Concurrente", "person-01"); return 201; }
                    catch (RechazoPerfil rejected) { assertEquals("LIMITE_PERFILES_ALCANZADO", rejected.codigo()); return 409; }
                };
                var a = executor.submit(attempt); var b = executor.submit(attempt);
                assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
                var results = new ArrayList<>(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))); Collections.sort(results);
                assertEquals(List.of(201, 409), results);
                tx.executeWithoutResult(status -> {
                    assertEquals(4, profiles.consultar(owner, EstadoPerfil.ACTIVO).size());
                    assertEquals(4, profiles.countByIdCuidadorAndEstado(owner, EstadoPerfil.ACTIVO));
                    assertEquals(5L, em.createQuery("select count(c) from ConfiguracionPerfil c where c.perfilUsuario.idCuidador = :id", Long.class).setParameter("id", owner).getSingleResult());
                });
            } finally {
                executor.shutdownNow(); assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
                // Delete only rows owned by the account created by this test.
                tx.executeWithoutResult(status -> {
                    em.createQuery("delete from ConfiguracionPerfil c where c.perfilUsuario.idCuidador = :id").setParameter("id", owner).executeUpdate();
                    em.createQuery("delete from PerfilUsuario p where p.idCuidador = :id").setParameter("id", owner).executeUpdate();
                    em.createQuery("delete from Cuidador c where c.idCuidador = :id").setParameter("id", owner).executeUpdate();
                });
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
