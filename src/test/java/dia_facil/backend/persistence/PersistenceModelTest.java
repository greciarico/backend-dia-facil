package dia_facil.backend.persistence;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.FetchType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.entity.CodigoAcceso;
import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.entity.ConfiguracionPerfil;
import dia_facil.backend.persistence.entity.Pictograma;
import dia_facil.backend.persistence.entity.PalabraClavePictograma;
import dia_facil.backend.persistence.entity.PlantillaActividad;
import dia_facil.backend.persistence.entity.PalabraClaveActividad;
import dia_facil.backend.persistence.entity.PlantillaPaso;
import dia_facil.backend.persistence.entity.Actividad;
import dia_facil.backend.persistence.entity.PasoActividad;
import dia_facil.backend.persistence.entity.Dispositivo;
import dia_facil.backend.persistence.entity.EjecucionActividad;
import dia_facil.backend.persistence.entity.ProgresoPaso;
import dia_facil.backend.persistence.entity.SolicitudAyuda;
import dia_facil.backend.persistence.entity.NotificacionCuidador;
import dia_facil.backend.persistence.entity.TokenPush;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates MySQL DDL into target only. No Spring context, datasource or SQL execution.
 * The fixture is a snapshot of the approved SQL model, not of the entity annotations.
 */
class PersistenceModelTest {
    static final List<Class<?>> ENTITIES = List.of(
        Cuidador.class,
        dia_facil.backend.persistence.entity.ConfiguracionCuidador.class,
        CodigoAcceso.class,
        PerfilUsuario.class,
        ConfiguracionPerfil.class,
        Pictograma.class,
        dia_facil.backend.persistence.entity.IconoActividad.class,
        dia_facil.backend.persistence.entity.PalabraClaveIconoActividad.class,
        PalabraClavePictograma.class,
        PlantillaActividad.class,
        PalabraClaveActividad.class,
        PlantillaPaso.class,
        Actividad.class,
        PasoActividad.class,
        Dispositivo.class,
        dia_facil.backend.persistence.entity.CredencialDispositivo.class,
        EjecucionActividad.class,
        ProgresoPaso.class,
        SolicitudAyuda.class,
        NotificacionCuidador.class,
        TokenPush.class,
        dia_facil.backend.persistence.entity.ControlRegistroPush.class,
        dia_facil.backend.persistence.entity.EntregaPush.class,
        dia_facil.backend.persistence.entity.SesionCuidador.class,
        dia_facil.backend.persistence.entity.CredencialSesionCuidador.class
    );
    private static StandardServiceRegistry registry;
    private static MetadataImplementor metadata;
    private static SessionFactory sessionFactory;
    private static Map<String, Table> tables;
    private static List<String[]> reference;
    private static String ddl;

    @BeforeAll
    static void buildModelWithoutDatabase() throws Exception {
        RejectConnections.attempts.set(0);
        registry = registryWithoutDatabase();
        var sources = new MetadataSources(registry);
        ENTITIES.forEach(sources::addAnnotatedClass);
        metadata = (MetadataImplementor) sources.buildMetadata();
        metadata.validate();

        var output = Path.of("target", "jpa-schema.sql");
        Files.createDirectories(output.getParent());
        var settings = new HashMap<String, Object>();
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("hibernate.hbm2ddl.halt_on_error", true);
        try (var writer = Files.newBufferedWriter(output)) {
            settings.put("jakarta.persistence.schema-generation.scripts.create-target", writer);
            SchemaManagementToolCoordinator.process(metadata, registry, settings, null);
        }
        ddl = Files.readString(output).toLowerCase();
        sessionFactory = metadata.buildSessionFactory();
        tables = metadata.getEntityBindings().stream()
            .map(binding -> binding.getTable())
            .collect(Collectors.toMap(Table::getName, table -> table));
        try (var resource = PersistenceModelTest.class.getResourceAsStream("/persistence/sql-v7-model.tsv")) {
            assertNotNull(resource);
            reference = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("\t", -1)).toList();
        }
    }

    @AfterAll
    static void closeModel() {
        try {
            if (sessionFactory != null) sessionFactory.close();
        } finally {
            if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
        }
        assertEquals(0, RejectConnections.attempts.get(), "No JDBC connection is permitted");
    }

    @Test
    void tablesColumnsPrimaryKeysAndTypesMatchReference() {
        assertEquals(25, tables.size());
        assertEquals(rows("T").stream().map(row -> row[1]).collect(Collectors.toSet()), tables.keySet());
        for (var row : rows("T")) {
            var table = tables.get(row[1]);
            assertEquals(List.of(row[3]), names(table.getPrimaryKey().getColumns()), row[1]);
            assertEquals(rows("C").stream().filter(c -> c[1].equals(row[1]))
                .map(c -> c[2]).collect(Collectors.toSet()), new TreeSet<>(names(table.getColumns())), row[1]);
        }
        for (var row : rows("C")) {
            var table = tables.get(row[1]);
            var column = table.getColumn(new Column(row[2]));
            assertNotNull(column, row[1] + "." + row[2]);
            assertEquals(Boolean.parseBoolean(row[4]), column.isNullable(), row[1] + "." + row[2]);
            assertSqlType(row[3], column.getSqlType(metadata), row[1] + "." + row[2]);
            assertEquals(row[5].equals("-") ? null : row[5].toUpperCase(),
                column.getDefaultValue() == null ? null : column.getDefaultValue().toUpperCase(),
                row[1] + "." + row[2] + " default");
            assertEquals(Boolean.parseBoolean(row[6]), column.isIdentity(), row[1] + "." + row[2]);
            assertEquals(Boolean.parseBoolean(row[7]),
                column.getOptions() != null && column.getOptions().toUpperCase().contains("ON UPDATE CURRENT_TIMESTAMP"),
                row[1] + "." + row[2] + " update timestamp");
        }
    }

    @Test
    void foreignKeysPreserveTargetsAndDatabaseDeleteActions() {
        assertEquals(rows("F").size(), tables.values().stream()
            .mapToInt(table -> table.getForeignKeyCollection().size()).sum());
        for (var row : rows("F")) {
            var fk = tables.get(row[1]).getForeignKeyCollection().stream()
                .filter(key -> key.getName().equals(row[2])).findFirst().orElseThrow();
            assertTrue(fk.isCreationEnabled(), row[2]);
            assertEquals(row[4], fk.getReferencedTable().getName(), row[2]);
            var referenced = fk.isReferenceToPrimaryKey()
                ? fk.getReferencedTable().getPrimaryKey().getColumns() : fk.getReferencedColumns();
            var actualPairs = new LinkedHashMap<String, String>();
            for (int i = 0; i < fk.getColumns().size(); i++) {
                actualPairs.put(fk.getColumns().get(i).getName(), referenced.get(i).getName());
            }
            var expectedPairs = new LinkedHashMap<String, String>();
            var local = row[3].split(",");
            var remote = row[5].split(",");
            for (int i = 0; i < local.length; i++) expectedPairs.put(local[i], remote[i]);
            assertEquals(expectedPairs, actualPairs, row[2]);
            if (!fk.isReferenceToPrimaryKey()) {
                assertTrue(fk.getReferencedTable().getUniqueKeys().values().stream()
                    .anyMatch(key -> names(key.getColumns()).equals(names(referenced))),
                    row[2] + " requires a matching ordered unique key in MySQL");
            }
            assertEquals(row[6].toLowerCase().replace(' ', '_'),
                fk.getOnDeleteAction().name().toLowerCase(), row[2]);
            assertTrue(ddl.contains("constraint " + row[2]), row[2] + " must be exported");
        }
    }

    @Test
    void activityIconsAreSeparatedFromBothKindsOfStepPictograms() throws Exception {
        for (var entity : List.of(Actividad.class, PlantillaActividad.class)) {
            var field = entity.getDeclaredField("iconoActividad");
            assertEquals(dia_facil.backend.persistence.entity.IconoActividad.class, field.getType());
            assertTrue(field.getAnnotation(ManyToOne.class).optional());
            assertThrows(NoSuchFieldException.class, () -> entity.getDeclaredField("pictograma"));
        }
        for (var entity : List.of(PasoActividad.class, PlantillaPaso.class)) {
            assertEquals(Pictograma.class, entity.getDeclaredField("pictograma").getType());
        }
    }

    @Test
    void initializerRepositoryQueriesCompileAgainstTheRealModelWithoutJdbc() {
        try (var real = sessionFactory.createEntityManager()) {
            var em = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
            org.mockito.Mockito.when(em.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Class.class)))
                .thenAnswer(call -> {
                    var query = org.mockito.Mockito.spy(real.createQuery(call.getArgument(0), (Class<?>) call.getArgument(1)));
                    org.mockito.Mockito.doReturn(List.of()).when(query).getResultList();
                    org.mockito.Mockito.doReturn(0L).when(query).getSingleResult();
                    return query;
                });
            var store = new dia_facil.backend.persistence.repository.JpaDatosBaseStore(em);
            assertTrue(store.icono("bread").isEmpty()); assertTrue(store.pictograma("Comer").isEmpty());
            assertTrue(store.plantilla("Desayunar").isEmpty()); assertTrue(store.pasos(1).isEmpty());
            assertFalse(store.palabraIcono(1, "pan")); assertFalse(store.palabraPictograma(1, "comer"));
            assertFalse(store.palabraPlantilla(1, "desayunar"));
        }
    }

    @Test
    void uniqueConstraintsIndexesAndChecksMatchReference() {
        for (var row : rows("U")) {
            var key = tables.get(row[1]).getUniqueKeys().get(row[2]);
            assertNotNull(key, row[2]);
            assertEquals(Set.of(row[3].split(",")), Set.copyOf(names(key.getColumns())), row[2]);
            assertTrue(ddl.contains(row[2]), row[2] + " must be exported");
        }
        for (var row : rows("I")) {
            var index = tables.get(row[1]).getIndexes().get(row[2]);
            assertNotNull(index, row[2]);
            assertFalse(index.isUnique(), row[2]);
            assertEquals(List.of(row[3].split(",")), names(index.getColumns()), row[2]);
            assertTrue(ddl.contains(row[2]), row[2] + " must be exported");
        }
        for (var row : rows("K")) {
            var check = tables.get(row[1]).getChecks().stream()
                .filter(c -> row[2].equals(c.getName())).findFirst().orElseThrow();
            assertEquals(normalizeExpression(row[3]), normalizeExpression(check.getConstraint()), row[2]);
            assertTrue(ddl.contains(row[2]), row[2] + " must be exported");
        }
    }

    @Test
    void associationsAreLazyUnidirectionalAndHaveNoJpaCascades() {
        for (var entity : ENTITIES) {
            for (var field : entity.getDeclaredFields()) {
                assertNull(field.getAnnotation(OneToMany.class), field.toString());
                assertNull(field.getAnnotation(ManyToMany.class), field.toString());
                var many = field.getAnnotation(ManyToOne.class);
                if (many != null) {
                    assertEquals(FetchType.LAZY, many.fetch(), field.toString());
                    assertEquals(0, many.cascade().length, field.toString());
                }
                var one = field.getAnnotation(OneToOne.class);
                if (one != null) {
                    assertEquals(FetchType.LAZY, one.fetch(), field.toString());
                    assertEquals(0, one.cascade().length, field.toString());
                    assertFalse(one.orphanRemoval(), field.toString());
                }
            }
        }
    }

    @Test
    void caregiverColumnHasOnlyOneWriter() throws Exception {
        var perfil = metadata.getEntityBinding(PerfilUsuario.class.getName());
        var caregiver = perfil.getProperty("cuidador");
        var caregiverId = perfil.getProperty("idCuidador");
        assertEquals(List.of("id_cuidador"), names(caregiver.getValue().getColumns()));
        assertEquals(List.of("id_cuidador"), names(caregiverId.getValue().getColumns()));
        assertTrue(caregiver.isInsertable());
        assertTrue(caregiver.isUpdatable());
        assertFalse(caregiverId.isInsertable());
        assertFalse(caregiverId.isUpdatable());
        assertThrows(NoSuchMethodException.class,
            () -> PerfilUsuario.class.getMethod("setIdCuidador", Integer.class));
        assertEquals(1, perfil.getPropertyClosure().stream()
            .filter(property -> property.isInsertable())
            .filter(property -> names(property.getValue().getColumns()).contains("id_cuidador"))
            .count());
        assertEquals(1, perfil.getPropertyClosure().stream()
            .filter(property -> property.isUpdatable())
            .filter(property -> names(property.getValue().getColumns()).contains("id_cuidador"))
            .count());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entityRegistrationOrders")
    void modelBuildsRegardlessOfEntityRegistrationOrder(String order, List<Class<?>> entities) {
        var orderedRegistry = registryWithoutDatabase();
        try {
            var sources = new MetadataSources(orderedRegistry);
            entities.forEach(sources::addAnnotatedClass);
            var orderedMetadata = (MetadataImplementor) sources.buildMetadata();
            orderedMetadata.validate();
            try (var orderedFactory = orderedMetadata.buildSessionFactory()) {
                assertTrue(orderedFactory.isOpen(), order);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(orderedRegistry);
        }
        assertEquals(0, RejectConnections.attempts.get(), order);
    }

    private static Stream<Arguments> entityRegistrationOrders() {
        return Stream.of(
            Arguments.of("original", ENTITIES),
            Arguments.of("alphabetical", ENTITIES.stream()
                .sorted(Comparator.comparing(Class::getName)).toList()),
            Arguments.of("reverse", ENTITIES.reversed())
        );
    }

    private static StandardServiceRegistry registryWithoutDatabase() {
        return new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.connection.provider_class", RejectConnections.class)
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .applySetting("jakarta.persistence.schema-generation.database.action", "none")
            .build();
    }

    @Test
    void hashLengthsMatchExistingBinary32Columns() {
        for (String table : List.of("credencial_dispositivo", "credencial_sesion_cuidador")) {
            var hashes = tables.get(table).getColumns().stream()
                .filter(column -> column.getName().endsWith("_hash")).toList();
            assertFalse(hashes.isEmpty());
            for (var column : hashes) assertEquals(Long.valueOf(32), column.getLength(), table + "." + column.getName());
        }
    }

    @Test
    void schemaContainsOnlyDdlAndTableStorageOptions() {
        assertEquals(ENTITIES.size(), Pattern.compile("create table ").matcher(ddl).results().count());
        assertEquals(ENTITIES.size(), Pattern.compile("engine=innodb").matcher(ddl).results().count());
        assertEquals(ENTITIES.size(), Pattern.compile("collate=utf8mb4_unicode_ci").matcher(ddl).results().count());
        assertFalse(ddl.contains("create trigger"));
        assertFalse(ddl.contains("insert into"));
        assertEquals(0, RejectConnections.attempts.get());
    }

    private static List<String[]> rows(String kind) {
        return reference.stream().filter(row -> row[0].equals(kind)).toList();
    }

    private static List<String> names(java.util.Collection<Column> columns) {
        return columns.stream().map(Column::getName).toList();
    }

    private static String normalizeExpression(String expression) {
        return expression.replaceAll("\\s+", "").toLowerCase();
    }

    private static void assertSqlType(String expected, String actual, String label) {
        if (expected.startsWith("ENUM")) {
            assertTrue(actual.toLowerCase().replaceAll("\\s+", "").startsWith("enum("), label + ": " + actual);
            var values = Pattern.compile("'([^']+)'");
            var expectedValues = values.matcher(expected).results().map(match -> match.group(1)).collect(Collectors.toSet());
            var actualValues = values.matcher(actual).results().map(match -> match.group(1)).collect(Collectors.toSet());
            assertEquals(expectedValues, actualValues, label);
        } else {
            // Display width and explicit zero fractional precision do not change the model semantics.
            var normalizedExpected = expected.toLowerCase().replace("tinyint(1)", "tinyint");
            var normalizedActual = actual.toLowerCase().replace("datetime(0)", "datetime").replace("time(0)", "time")
                .replace("integer", "int");
            assertEquals(normalizedExpected, normalizedActual, label);
        }
    }

    /** Any unexpected JDBC use fails the test instead of touching a developer's database. */
    public static class RejectConnections implements ConnectionProvider {
        static final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Connection getConnection() throws SQLException {
            attempts.incrementAndGet();
            throw new SQLException("Database access is forbidden in metadata tests");
        }

        @Override
        public void closeConnection(Connection connection) {
            throw new AssertionError("No connection should have been opened");
        }

        @Override
        public boolean supportsAggressiveRelease() { return false; }

        @Override
        public boolean isUnwrappableAs(Class<?> type) { return type.isInstance(this); }

        @Override
        public <T> T unwrap(Class<T> type) {
            if (type.isInstance(this)) return type.cast(this);
            throw new IllegalArgumentException(type.getName());
        }
    }
}

