package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import dia_facil.backend.persistence.repository.*;
import java.time.LocalDateTime;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import static org.junit.jupiter.api.Assertions.*;

class VinculacionRepositoryTest {
    @Test void consultasYHoraLocalCompilanConModeloRealSinConectarseABd() throws Exception {
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.connection.provider_class", PersistenceModelTest.RejectConnections.class)
            .applySetting("hibernate.hbm2ddl.auto", "none").build();
        var sources = new MetadataSources(registry); PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
        try (var factory = sources.buildMetadata().buildSessionFactory(); var em = factory.createEntityManager()) {
            assertNotNull(new JpaRepositoryFactory(em).getRepository(DispositivoRepository.class));
            assertNotNull(new JpaRepositoryFactory(em).getRepository(PerfilUsuarioRepository.class));
            var hql = PerfilUsuarioRepository.class.getMethod("bloquear", Integer.class, Integer.class, EstadoPerfil.class).getAnnotation(Query.class).value();
            var query = em.createQuery(hql, PerfilUsuario.class).setParameter("perfil", 12).setParameter("cuidador", 3).setParameter("estado", EstadoPerfil.ACTIVO);
            assertEquals(3, query.getParameterValue("cuidador"));
            assertNotNull(em.createQuery("select local datetime", LocalDateTime.class));
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
