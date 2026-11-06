package dia_facil.backend.persistence;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.persistence.repository.ActividadRepository;
import dia_facil.backend.service.perfiles.GeneradorCodigoPerfil;
import java.time.*;
import java.util.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** No DDL. Fixtures are always rolled back, in an explicitly supplied disposable test schema. */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL",matches=".+")
class AgendaUsuarioMySqlTest {
    @Test void fechaEnJoinExcluyeHistoricoYNoOcultaPendientesConOrdenEstable() throws Exception {
        String url=System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"));
        assertFalse(url.toLowerCase(Locale.ROOT).contains("databaseterm="));
        var ds=new DriverManagerDataSource(url,System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try(var connection=ds.getConnection()){assertTrue(connection.getCatalog().startsWith("diafacil_test_"));}
        var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource",ds)
            .applySetting("hibernate.hbm2ddl.auto","none").build();
        var sources=new MetadataSources(registry);PersistenceModelTest.ENTITIES.forEach(sources::addAnnotatedClass);
        try(var factory=sources.buildMetadata().buildSessionFactory();var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                var owner=Cuidador.crear(UUID.randomUUID()+"@example.invalid","Test");em.persist(owner);
                var generator=new GeneradorCodigoPerfil();
                var profile=PerfilUsuario.crear(owner,"Test",null,generator.generar());em.persist(profile);
                var other=PerfilUsuario.crear(owner,"Other",null,generator.generar());em.persist(other);
                var historical=Actividad.crear(profile,"Yesterday",LocalTime.of(9,0),null);em.persist(historical);
                var pending=Actividad.crear(profile,"No execution",LocalTime.of(7,0),null);em.persist(pending);
                var inProgress=Actividad.crear(profile,"In progress",LocalTime.of(7,0),null);em.persist(inProgress);
                var complete=Actividad.crear(profile,"Completed",LocalTime.of(8,0),null);em.persist(complete);
                var inactive=Actividad.crear(profile,"Inactive",LocalTime.of(6,0),null);inactive.setEstado(EstadoActividad.INACTIVA);em.persist(inactive);
                em.persist(Actividad.crear(other,"Other profile",LocalTime.of(6,0),null));
                var today=LocalDate.of(2026,10,24);
                for(var item:List.of(historical,inProgress,complete)) {
                    var execution=BeanUtils.instantiateClass(EjecucionActividad.class.getDeclaredConstructor());
                    execution.setActividad(item);execution.setFecha(item==historical?today.minusDays(1):today);
                    if(item!=inProgress){execution.setEstado(EstadoEjecucion.COMPLETADA);execution.setModoFinalizacion(ModoFinalizacion.DIRECTA);execution.setFechaFin(LocalDateTime.of(2026,10,24,12,0));}
                    em.persist(execution);
                }
                em.flush();
                var repo=new JpaRepositoryFactory(em).getRepository(ActividadRepository.class);
                var rows=repo.agenda(profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA);
                assertEquals(List.of(pending.getIdActividad(),inProgress.getIdActividad(),complete.getIdActividad(),historical.getIdActividad()),rows.stream().map(r->r.idActividad()).toList());
                assertEquals(List.of("PENDIENTE","EN_PROGRESO","COMPLETADA","PENDIENTE"),rows.stream().map(r->r.estado()).toList());
                var historicalDetail=repo.detalleUsuario(historical.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).orElseThrow();
                assertEquals("PENDIENTE",historicalDetail.estado());assertFalse(historicalDetail.guiaInicialCompletada());
                historical.setGuiaInicialCompletada(true);em.flush();
                assertTrue(repo.detalleUsuario(historical.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).orElseThrow().guiaInicialCompletada());
                assertEquals("EN_PROGRESO",repo.detalleUsuario(inProgress.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).orElseThrow().estado());
                assertEquals("COMPLETADA",repo.detalleUsuario(complete.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).orElseThrow().estado());
                assertTrue(repo.detalleUsuario(pending.getIdActividad(),other.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).isEmpty());
                assertTrue(repo.detalleUsuario(inactive.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).isEmpty());
                profile.setEstado(EstadoPerfil.INACTIVO);em.flush();
                assertTrue(repo.agenda(profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).isEmpty());
                assertTrue(repo.detalleUsuario(pending.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).isEmpty());
            } finally {if(em.getTransaction().isActive())em.getTransaction().rollback();}
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }
}
