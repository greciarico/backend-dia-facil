package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoPerfil;
import java.time.LocalDateTime;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsultasDispositivoTest {
    @Test void consultasDeVinculacionYTransicionCompilanContraModeloReal() {
        var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.dialect","org.hibernate.dialect.MySQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access",false)
            .applySetting("hibernate.connection.provider_class",PersistenceModelTest.RejectConnections.class)
            .applySetting("hibernate.hbm2ddl.auto","none").build();
        var sources=new MetadataSources(registry);PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
        try(var factory=sources.buildMetadata().buildSessionFactory();var em=factory.createEntityManager()) {
            assertNotNull(em.createQuery("select e from EjecucionActividad e where e.actividad.idActividad=:actividad and e.fecha=:fecha",EjecucionActividad.class));
            assertNotNull(em.createQuery("select p from PasoActividad p left join fetch p.pictograma where p.actividad.idActividad=:actividad and p.activo=true order by p.orden,p.idPasoActividad",PasoActividad.class));
            assertNotNull(em.createQuery("select p from ProgresoPaso p join fetch p.pasoActividad where p.ejecucionActividad.idEjecucionActividad=:ejecucion",ProgresoPaso.class));
            var repository=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em)
                .getRepository(dia_facil.backend.persistence.repository.ActividadRepository.class);
            assertNotNull(repository); // Spring Data validates both projection queries against the real mapping.
            assertNotNull(em.createQuery("select c from CredencialDispositivo c join fetch c.dispositivo d where c.tokenHash=:hash and d.perfilUsuario.idPerfilUsuario=:perfil and d.estado=:estado",CredencialDispositivo.class));
            assertNotNull(em.createQuery("select c from CredencialDispositivo c where c.tokenHash=:hash",CredencialDispositivo.class).setParameter("hash",new byte[32]));
            assertNotNull(em.createQuery("select c from CredencialDispositivo c where 1=0"));
            assertNotNull(em.createQuery("select p.idPerfilUsuario from PerfilUsuario p where p.codigoVinculacion=:codigo and p.estado=:estado",Integer.class)
                .setParameter("codigo","DF-7KQ9W6H2M8V3X5RA").setParameter("estado",EstadoPerfil.ACTIVO));
            assertNotNull(em.createQuery("select p.codigoVinculacion from PerfilUsuario p",String.class));
            assertNotNull(em.createQuery("select p from PerfilUsuario p order by p.idPerfilUsuario",PerfilUsuario.class));
            assertNotNull(em.createQuery("delete from CredencialDispositivo c where c.dispositivo is null and c.preparacionHasta <= :ahora")
                .setParameter("ahora",LocalDateTime.now()));
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }
}
