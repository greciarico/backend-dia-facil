package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoDispositivo;
import dia_facil.backend.persistence.repository.*;
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

/** Opt-in, existing disposable test schema only. Never uses application datasource or DDL. */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_MYSQL_URL", matches = ".+")
class VinculacionMySqlTest {
    @Test void renovacionesConcurrentesConMismoCodigoSoloPermitenUnCambioYSinDesvincular() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_MYSQL_USER"), System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try (var connection = ds.getConnection()) { assertTrue(connection.getCatalog().startsWith("diafacil_test_")); }
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", ds)
            .applySetting("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
            .applySetting("hibernate.hbm2ddl.auto", "none").build();
        var sources = new MetadataSources(registry); PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
        try (var factory = sources.buildMetadata().buildSessionFactory()) {
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            var manager = new JpaTransactionManager(factory); var tx = new TransactionTemplate(manager);
            var repos = new JpaRepositoryFactory(em);
            var profiles = repos.getRepository(PerfilUsuarioRepository.class);
            var devices = repos.getRepository(DispositivoRepository.class);
            var generator = new GeneradorCodigoPerfil(); String original = generator.generar();
            var ids = Objects.requireNonNull(tx.execute(status -> {
                var owner = Cuidador.crear(UUID.randomUUID() + "@example.invalid", "Test"); em.persist(owner);
                var profile = PerfilUsuario.crear(owner, "Test", null, original); em.persist(profile); em.flush();
                // Device has no public creation API in E1: fixture via native insert, only within this test profile.
                em.createNativeQuery("insert into dispositivo (id_perfil_usuario,nombre,estado) values (:p,'Test','ACTIVO'),(:p,'Old','DESVINCULADO')")
                    .setParameter("p", profile.getIdPerfilUsuario()).executeUpdate();
                return List.of(owner.getIdCuidador(), profile.getIdPerfilUsuario());
            }));
            var target = new VinculacionTransaccionalService(profiles, devices, generator, em);
            var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var service = new VinculacionService((VinculacionTransaccionalService) proxy.getProxy());
            var pool = Executors.newFixedThreadPool(2);
            try {
                assertEquals(1, service.consultar(ids.get(0), ids.get(1)).dispositivosActivos());
                var start = new CountDownLatch(1);
                Callable<String> renewal = () -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try { service.renovar(ids.get(0), ids.get(1), original); return "ok"; }
                    catch (RechazoPerfil rejection) { return rejection.codigo(); }
                };
                var a = pool.submit(renewal); var b = pool.submit(renewal); start.countDown();
                assertEquals(Set.of("ok", "CODIGO_VINCULACION_CAMBIADO"), Set.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
                var result = service.consultar(ids.get(0), ids.get(1)); assertNotEquals(original, result.codigoVinculacion());
                assertEquals(1, result.dispositivosActivos());
                assertTrue(result.renovacionEnSegundos() > 0 && result.renovacionEnSegundos() <= 60);
                assertEquals("RENOVACION_PREMATURA", assertThrows(RechazoPerfil.class,
                    () -> service.renovar(ids.get(0), ids.get(1), result.codigoVinculacion())).codigo());
                tx.executeWithoutResult(status -> {
                    var profile = em.find(PerfilUsuario.class, ids.get(1)); assertNotNull(profile.getFechaRenovacionCodigo());
                    assertEquals(1, devices.countByPerfilUsuario_IdPerfilUsuarioAndEstado(ids.get(1), EstadoDispositivo.DESVINCULADO));
                });
                assertEquals("PERFIL_NO_ENCONTRADO", assertThrows(RechazoPerfil.class, () -> service.consultar(-1, ids.get(1))).codigo());
            } finally {
                pool.shutdownNow(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
                tx.executeWithoutResult(status -> em.createQuery("delete from Cuidador c where c.idCuidador=:id")
                    .setParameter("id", ids.get(0)).executeUpdate());
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
