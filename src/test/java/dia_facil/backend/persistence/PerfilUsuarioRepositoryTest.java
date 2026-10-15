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
                .addAnnotatedClass(PerfilUsuario.class)
                .addAnnotatedClass(dia_facil.backend.persistence.entity.Actividad.class)
                .addAnnotatedClass(dia_facil.backend.persistence.entity.Pictograma.class).buildMetadata().buildSessionFactory();
                var em = factory.createEntityManager()) {
            assertNotNull(new JpaRepositoryFactory(em).getRepository(PerfilUsuarioRepository.class));
            var hql = PerfilUsuarioRepository.class.getMethod("consultar", Integer.class, EstadoPerfil.class)
                .getAnnotation(Query.class).value();
            // Validates constructor projection, both bound parameters and the mapped properties/order.
            var query = em.createQuery(hql, PerfilResumen.class).setParameter("cuidador", 3).setParameter("estado", EstadoPerfil.ACTIVO);
            assertEquals(3, query.getParameterValue("cuidador"));
            assertEquals(EstadoPerfil.ACTIVO, query.getParameterValue("estado"));
            var detailHql = PerfilUsuarioRepository.class.getMethod("detalle", Integer.class, Integer.class, EstadoPerfil.class)
                .getAnnotation(Query.class).value();
            var detail = em.createQuery(detailHql, PerfilResumen.class).setParameter("perfil", 12)
                .setParameter("cuidador", 3).setParameter("estado", EstadoPerfil.ACTIVO);
            assertEquals(12, detail.getParameterValue("perfil"));
            assertEquals(3, detail.getParameterValue("cuidador"));
            assertEquals(EstadoPerfil.ACTIVO, detail.getParameterValue("estado"));
            var activitiesRepository = dia_facil.backend.persistence.repository.ActividadRepository.class;
            var active = dia_facil.backend.persistence.enums.EstadoActividad.ACTIVA;
            assertNotNull(new JpaRepositoryFactory(em).getRepository(activitiesRepository));
            var activitiesHql = activitiesRepository.getMethod("consultar", Integer.class, Integer.class, EstadoPerfil.class,
                dia_facil.backend.persistence.enums.EstadoActividad.class).getAnnotation(Query.class).value();
            var activities = em.createQuery(activitiesHql, dia_facil.backend.service.actividades.ActividadResumen.class)
                .setParameter("perfil", 12).setParameter("cuidador", 3).setParameter("estadoPerfil", EstadoPerfil.ACTIVO)
                .setParameter("estadoActividad", active);
            assertEquals(12, activities.getParameterValue("perfil"));
            assertEquals(3, activities.getParameterValue("cuidador"));
            assertEquals(EstadoPerfil.ACTIVO, activities.getParameterValue("estadoPerfil"));
            assertEquals(active, activities.getParameterValue("estadoActividad"));
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
