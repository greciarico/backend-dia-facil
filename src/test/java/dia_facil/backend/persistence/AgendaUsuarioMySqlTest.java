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
                    em.persist(VersionActividad.de(item));
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
                inProgress.setRevision(2);
                inProgress.setNombre("Versión nueva");
                inProgress.setHora(LocalTime.of(20, 0));
                em.persist(VersionActividad.de(inProgress));
                em.flush();
                var hoy = repo.detalleUsuario(inProgress.getIdActividad(), profile.getIdPerfilUsuario(), today,
                    EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).orElseThrow();
                assertEquals("In progress", hoy.nombre());
                assertEquals("07:00", hoy.hora());
                assertEquals("EN_PROGRESO", hoy.estado());
                var siguiente = repo.detalleUsuario(inProgress.getIdActividad(), profile.getIdPerfilUsuario(), today.plusDays(1),
                    EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).orElseThrow();
                assertEquals("Versión nueva", siguiente.nombre());
                assertEquals("20:00", siguiente.hora());
                assertEquals("PENDIENTE", siguiente.estado());
                inProgress.setEstado(EstadoActividad.INACTIVA);
                complete.setEstado(EstadoActividad.INACTIVA);
                em.flush();
                assertEquals("EN_PROGRESO", repo.detalleUsuario(inProgress.getIdActividad(), profile.getIdPerfilUsuario(), today,
                    EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).orElseThrow().estado());
                assertEquals("COMPLETADA", repo.detalleUsuario(complete.getIdActividad(), profile.getIdPerfilUsuario(), today,
                    EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).orElseThrow().estado());
                assertEquals(4, repo.agenda(profile.getIdPerfilUsuario(), today, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).size());
                assertEquals(2, repo.agenda(profile.getIdPerfilUsuario(), today.plusDays(1), EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).size());
                profile.setEstado(EstadoPerfil.INACTIVO);em.flush();
                assertTrue(repo.agenda(profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).isEmpty());
                assertTrue(repo.detalleUsuario(pending.getIdActividad(),profile.getIdPerfilUsuario(),today,EstadoPerfil.ACTIVO,EstadoActividad.ACTIVA).isEmpty());
                comprobarOrdenCuidador(em, repo, owner, other);
            } finally {if(em.getTransaction().isActive())em.getTransaction().rollback();}
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }

    private void comprobarOrdenCuidador(jakarta.persistence.EntityManager em, ActividadRepository repo,
                                        Cuidador owner, PerfilUsuario other) {
        var profile = PerfilUsuario.crear(owner, "Rutina", null, new GeneradorCodigoPerfil().generar());
        em.persist(profile);
        em.flush();
        var profileId = profile.getIdPerfilUsuario();
        var ownerId = owner.getIdCuidador();
        assertTrue(repo.consultar(profileId, ownerId, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).isEmpty());

        var night = Actividad.crear(profile, "Dormir", LocalTime.of(21, 0), null);
        em.persist(night);
        em.flush();
        assertEquals(List.of(night.getIdActividad()), repo.consultar(profileId, ownerId,
            EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).stream().map(r -> r.idActividad()).toList());

        var morning = Actividad.crear(profile, "Desayunar", LocalTime.of(8, 0), null);
        var midnight = Actividad.crear(profile, "Medianoche", LocalTime.MIDNIGHT, null);
        var noon = Actividad.crear(profile, "Almorzar", LocalTime.NOON, null);
        var sameMorning = Actividad.crear(profile, "Estudiar", LocalTime.of(8, 0), null);
        var inactive = Actividad.crear(profile, "Inactiva", LocalTime.of(6, 0), null);
        inactive.setEstado(EstadoActividad.INACTIVA);
        for (var activity : List.of(morning, midnight, noon, sameMorning, inactive)) em.persist(activity);
        em.flush();
        assertEquals(List.of(midnight.getIdActividad(), morning.getIdActividad(), sameMorning.getIdActividad(),
            noon.getIdActividad(), night.getIdActividad()), repo.consultar(profileId, ownerId,
            EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).stream().map(r -> r.idActividad()).toList());

        var afternoon = Actividad.crear(profile, "Caminar", LocalTime.of(15, 0), null);
        em.persist(afternoon);
        em.flush();
        assertEquals(List.of(midnight.getIdActividad(), morning.getIdActividad(), sameMorning.getIdActividad(),
            noon.getIdActividad(), afternoon.getIdActividad(), night.getIdActividad()), repo.consultar(profileId,
            ownerId, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).stream().map(r -> r.idActividad()).toList());
        assertEquals(1, repo.consultar(other.getIdPerfilUsuario(), ownerId, EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).size());

        var stranger = Cuidador.crear(UUID.randomUUID() + "@example.invalid", "Otro cuidador");
        em.persist(stranger);
        em.flush();
        assertTrue(repo.consultar(profileId, stranger.getIdCuidador(), EstadoPerfil.ACTIVO, EstadoActividad.ACTIVA).isEmpty());
    }
}
