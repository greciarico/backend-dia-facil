package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.CodigoAcceso;
import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.repository.CodigoAccesoRepository;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import static org.junit.jupiter.api.Assertions.*;

/** Validates repository bootstrap and query derivation without executing queries or accessing JDBC. */
class AuthenticationRepositoryTest {

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;
    private static EntityManager entityManager;
    private static JpaRepositoryFactory repositoryFactory;

    @BeforeAll
    static void buildRepositoriesWithoutDatabase() {
        registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.connection.provider_class", PersistenceModelTest.RejectConnections.class)
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .applySetting("jakarta.persistence.schema-generation.database.action", "none")
            .build();
        sessionFactory = new MetadataSources(registry)
            .addAnnotatedClass(Cuidador.class)
            .addAnnotatedClass(CodigoAcceso.class)
            .buildMetadata()
            .buildSessionFactory();
        entityManager = sessionFactory.createEntityManager();
        repositoryFactory = new JpaRepositoryFactory(entityManager);
    }

    @AfterAll
    static void closeRepositories() {
        try {
            if (entityManager != null) entityManager.close();
        } finally {
            try {
                if (sessionFactory != null) sessionFactory.close();
            } finally {
                if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
            }
        }
        assertEquals(0, PersistenceModelTest.RejectConnections.attempts.get(),
            "Repository validation must not open JDBC connections");
    }

    @Test
    void springDataCreatesBothRepositoryProxies() {
        assertNotNull(repositoryFactory.getRepository(CuidadorRepository.class));
        assertNotNull(repositoryFactory.getRepository(CodigoAccesoRepository.class));
    }

    @Test
    void caregiverLookupFiltersOnlyByEmail() throws NoSuchMethodException {
        var method = CuidadorRepository.class.getMethod("findByEmail", String.class);
        var query = new PartTree(method.getName(), Cuidador.class);

        assertEmailEqualityOnly(query);
        assertFalse(query.isLimiting());
        assertTrue(query.getSort().isUnsorted());
    }

    @Test
    void latestCodeLookupFiltersOnlyByEmailAndBreaksTimestampTiesById() throws NoSuchMethodException {
        var method = CodigoAccesoRepository.class.getMethod(
            "findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc", String.class);
        var query = new PartTree(method.getName(), CodigoAcceso.class);

        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE,
            method.getAnnotation(org.springframework.data.jpa.repository.Lock.class).value());

        // Used and expired issuances remain candidates; do not fall back to an older code.
        assertEmailEqualityOnly(query);
        assertTrue(query.isLimiting());
        assertEquals(Integer.valueOf(1), query.getMaxResults());
        assertEquals(List.of(
            Sort.Order.desc("fechaCreacion"),
            Sort.Order.desc("idCodigoAcceso")
        ), query.getSort().toList());
    }

    private static void assertEmailEqualityOnly(PartTree query) {
        var parts = query.getParts().toList();
        assertEquals(1, query.stream().count(), "The query must not contain alternative OR branches");
        assertEquals(1, parts.size(), "Only email should restrict the result");
        assertEquals("email", parts.getFirst().getProperty().toDotPath());
        assertEquals(Part.Type.SIMPLE_PROPERTY, parts.getFirst().getType());
        assertEquals(Part.IgnoreCaseType.NEVER, parts.getFirst().shouldIgnoreCase());
    }
}
