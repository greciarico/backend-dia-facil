package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.PerfilUsuarioRepository;
import dia_facil.backend.service.perfiles.PerfilResumen;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import static org.junit.jupiter.api.Assertions.*;

class PerfilUsuarioRepositoryTest {
    @Test void consultaProyectadaCompilaConElModeloRealSinJdbc() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.connection.provider_class", PersistenceModelTest.RejectConnections.class)
            .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(Cuidador.class)
                .addAnnotatedClass(PerfilUsuario.class).buildMetadata().buildSessionFactory();
                var em = factory.createEntityManager()) {
            assertNotNull(new JpaRepositoryFactory(em).getRepository(PerfilUsuarioRepository.class));
            var hql = PerfilUsuarioRepository.class.getMethod("consultar", Integer.class, EstadoPerfil.class)
                .getAnnotation(Query.class).value();
            // Validates constructor projection, both bound parameters and the mapped properties/order.
            var query = em.createQuery(hql, PerfilResumen.class).setParameter("cuidador", 3).setParameter("estado", EstadoPerfil.ACTIVO);
            assertEquals(3, query.getParameterValue("cuidador"));
            assertEquals(EstadoPerfil.ACTIVO, query.getParameterValue("estado"));
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
