package dia_facil.backend.service.push;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.repository.*;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.ayuda.SolicitudAyudaService;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import dia_facil.backend.service.perfiles.GeneradorCodigoPerfil;
import dia_facil.backend.service.perfiles.PerfilResumen;
import jakarta.persistence.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OPT-IN ONLY. A dedicated, disposable diafacil_test_* replica must already contain
 * both incremental migrations. No Boot context, application-local config, DDL,
 * schedulers or real Expo calls. The worker scans the whole queue: do not share
 * this schema with another running app/test. Cleanup removes only our fixture owners.
 * Uses the same DIAFACIL_TEST_MYSQL_URL/USER/PASSWORD variables as Increment 1.
 */
@EnabledIfEnvironmentVariable(named = "DIAFACIL_TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class PushMySqlTest {
    private static final String PROJECT = "12345678-1234-4234-8234-123456789abc";
    private static final List<String> ENTITIES = List.of(
        "Cuidador", "ConfiguracionCuidador", "CodigoAcceso", "PerfilUsuario", "ConfiguracionPerfil",
        "Pictograma", "IconoActividad", "PalabraClaveIconoActividad", "PalabraClavePictograma",
        "PlantillaActividad", "PalabraClaveActividad", "PlantillaPaso", "Actividad", "PasoActividad",
        "Dispositivo", "CredencialDispositivo", "EjecucionActividad", "ProgresoPaso", "SolicitudAyuda",
        "NotificacionCuidador", "TokenPush", "SesionCuidador", "CredencialSesionCuidador", "EntregaPush", "ControlRegistroPush");
    private StandardServiceRegistry registry;
    private EntityManagerFactory factory;
    private EntityManager em;
    private JpaTransactionManager tm;
    private TransactionTemplate tx;
    private RegistroPushService registrations;
    private NotificacionEventoService events;
    private SesionCuidadorService sessions;
    private final List<Integer> owners = new ArrayList<>();
    private Fixture a, b;

    private record Fixture(int owner, int profile, long session, String refresh) {
        SesionCuidadorService.Identidad identity() {
            return new SesionCuidadorService.Identidad(session,
                new SesionCuidadorService.CuidadorResumen(owner, "Push test", null));
        }
    }
    private record Delivery(long id, String state, int attempts, String ticket) {}
    private static String key() { return UUID.randomUUID().toString(); }
    private static String token() { return "ExpoPushToken[" + key().replace("-", "") + "]"; }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        var p = new ProxyFactory(target);
        p.setProxyTargetClass(true);
        p.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return (T) p.getProxy();
    }

    @BeforeAll void connectOnlyToPreparedDisposableSchema() throws Exception {
        String url = System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertNotNull(url);
        assertTrue(url.matches("jdbc:mysql://[^/;]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"),
            "Only a dedicated diafacil_test_* replica is permitted");
        assertFalse(url.toLowerCase(Locale.ROOT).matches(
            ".*(databaseterm|createdatabaseifnotexist|sessionvariables|initsql).*"));
        var ds = new DriverManagerDataSource(url, System.getenv("DIAFACIL_TEST_MYSQL_USER"),
            System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        try (var c = ds.getConnection()) {
            String address = url.split("\\?", 2)[0];
            assertEquals(address.substring(address.lastIndexOf('/') + 1), c.getCatalog());
            assertTrue(c.getCatalog().startsWith("diafacil_test_"));
            try (var s = c.createStatement(); var r = s.executeQuery(
                "SELECT id_instalacion,id_sesion,activo,generacion,proyecto_eas FROM token_push WHERE 1=0")) {
                assertFalse(r.next());
            }
            try (var s = c.createStatement(); var r = s.executeQuery(
                "SELECT generacion,estado,proxima_en,vence_en,arrendamiento FROM entrega_push WHERE 1=0")) {
                assertFalse(r.next());
            }
            for (String index : List.of("uq_entrega_destino", "uq_notif_solicitud", "uq_notif_ejecucion")) {
                try (var s = c.prepareStatement("SELECT COUNT(*) FROM information_schema.statistics "
                    + "WHERE table_schema=DATABASE() AND index_name=? AND non_unique=0")) {
                    s.setString(1, index);
                    try (var r = s.executeQuery()) { assertTrue(r.next()); assertTrue(r.getInt(1) > 0, index); }
                }
            }
        }
        registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.datasource", ds)
            .applySetting("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
            .applySetting("hibernate.hbm2ddl.auto", "none").build();
        var sources = new MetadataSources(registry);
        for (String name : ENTITIES) sources.addAnnotatedClass(
            Class.forName("dia_facil.backend.persistence.entity." + name));
        factory = sources.buildMetadata().buildSessionFactory();
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        tm = new JpaTransactionManager(factory);
        tm.setJpaDialect(new HibernateJpaDialect());
        tx = new TransactionTemplate(tm);
        var config = new PushProperties(true, PROJECT, null);
        registrations = proxy(new RegistroPushService(em, config));
        events = proxy(new NotificacionEventoService(em, config));
        var repos = new JpaRepositoryFactory(em);
        sessions = proxy(new SesionCuidadorService(repos.getRepository(SesionCuidadorRepository.class),
            repos.getRepository(CredencialSesionCuidadorRepository.class), repos.getRepository(CuidadorRepository.class),
            Clock.systemUTC(), new CriptografiaSesion(new SecureRandom()),
            Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(90)));
    }

    @BeforeEach void fixtures() {
        // A worker must never process deliveries belonging to another test/application.
        assertEquals(Long.valueOf(0), tx.<Long>execute(s -> em.createQuery("select count(e) from EntregaPush e", Long.class).getSingleResult()));
        a = fixture(); b = fixture();
    }
    private Fixture fixture() {
        return Objects.requireNonNull(tx.execute(s -> {
            var owner = Cuidador.crear(key() + "@example.invalid", "Push test"); em.persist(owner);
            var profile = PerfilUsuario.crear(owner, "Push test", null, new GeneradorCodigoPerfil().generar());
            em.persist(profile);
            var date = now();
            var session = SesionCuidador.crear(owner, date, date.plusDays(1), date.plusDays(2)); em.persist(session);
            var crypto = new CriptografiaSesion(new SecureRandom());
            String refresh = crypto.generar("r");
            em.persist(CredencialSesionCuidador.crear(session, 0,
                CriptografiaSesion.hash(crypto.generar("a")), CriptografiaSesion.hash(refresh), date.plusMinutes(15)));
            em.flush(); owners.add(owner.getIdCuidador());
            return new Fixture(owner.getIdCuidador(), profile.getIdPerfilUsuario(), session.getIdSesion(), refresh);
        }));
    }
    @AfterEach void cleanupOnlyFixtureOwners() {
        if (tx == null) return;
        for (int owner : owners) tx.executeWithoutResult(s -> {
            em.createQuery("delete from TokenPush t where t.cuidador.idCuidador=:id").setParameter("id", owner).executeUpdate();
            em.createQuery("delete from CredencialSesionCuidador c where c.sesion.cuidador.idCuidador=:id")
                .setParameter("id", owner).executeUpdate();
            em.createQuery("delete from SesionCuidador s where s.cuidador.idCuidador=:id").setParameter("id", owner).executeUpdate();
            em.createQuery("delete from Cuidador c where c.idCuidador=:id").setParameter("id", owner).executeUpdate();
        });
        owners.clear();
    }
    @AfterAll void disconnect() {
        if (factory != null) factory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }
    private RegistroPushService.Estado register(Fixture f, UUID id, String value) {
        return registrations.registrar(f.identity(), new RegistroPushService.Peticion(id, value, "ANDROID"), registrations.consultar(f.identity(), id).etag()).estado();
    }
    private int event(Fixture f) {
        return Objects.requireNonNull(tx.execute(s -> {
            var help = SolicitudAyuda.crear(em.find(PerfilUsuario.class, f.profile()), key(), null, null, null, null);
            em.persist(help);
            var n = NotificacionCuidador.ayuda(help); events.registrarNotificacion(n); em.flush();
            return n.getIdNotificacionCuidador();
        }));
    }
    private long count(String entity, Fixture f) {
        return Objects.requireNonNull(tx.execute(s -> em.createQuery("select count(n) from " + entity
            + " n where n.perfilUsuario.idPerfilUsuario=:id", Long.class).setParameter("id", f.profile()).getSingleResult()));
    }
    private List<Delivery> deliveries() {
        return tx.execute(s -> em.createQuery("select e from EntregaPush e order by e.idEntrega", EntregaPush.class)
            .getResultList().stream().map(e -> new Delivery(e.getIdEntrega(), e.getEstado(), e.getIntentos(), e.getTicket())).toList());
    }
    private void due(long id) {
        tx.executeWithoutResult(s -> em.find(EntregaPush.class, id).setProximaEn(now().minusSeconds(1)));
    }
    private EntregaPushWorker worker(ExpoPushClient expo) {
        return new EntregaPushWorker(em, tm, expo, new PushProperties(true, PROJECT, null));
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(20, TimeUnit.SECONDS), "Concurrent worker did not reach controlled checkpoint");
    }

    @Test void registroIdempotenteRotacionCambioCuentaYRevocacion() {
        UUID install = UUID.randomUUID(); String first = token();
        var initial = register(a, install, first);
        assertEquals(initial, register(a, install, first));
        var rotated = register(a, install, token());
        assertEquals(initial.idRegistro(), rotated.idRegistro());
        assertTrue(rotated.generacion() > initial.generacion());
        assertFalse(registrations.consultar(b.identity(), install).estado().activo());
        registrations.eliminar(b.identity(), install, registrations.consultar(b.identity(), install).etag());
        assertEquals(rotated, registrations.consultar(a.identity(), install).estado());
        var switched = register(b, install, token());
        assertEquals(initial.idRegistro(), switched.idRegistro());
        assertTrue(switched.generacion() > rotated.generacion());
        registrations.eliminar(a.identity(), install, registrations.consultar(a.identity(), install).etag()); // A late logout must not remove B's registration.
        assertEquals(switched, registrations.consultar(b.identity(), install).estado());
        sessions.cerrar(b.refresh());
        assertEquals(401, assertThrows(ResponseStatusException.class,
            () -> registrations.consultar(b.identity(), install)).getStatusCode().value());
        event(b); assertTrue(deliveries().isEmpty(), "Revoked sessions cannot receive new jobs");
    }

    @Test void eventoYColaRollbackSinRetroactividadNiDuplicadoPorReintento() {
        event(a); // No token: persist history, but never backfill it later.
        register(a, UUID.randomUUID(), token());
        register(b, UUID.randomUUID(), token());
        assertTrue(deliveries().isEmpty());
        long before = count("NotificacionCuidador", a);
        tx.executeWithoutResult(s -> { event(a); s.setRollbackOnly(); });
        assertEquals(before, count("NotificacionCuidador", a));
        assertTrue(deliveries().isEmpty());
        assertEquals(1L, count("SolicitudAyuda", a));

        var access = mock(AccesoDispositivoService.class);
        var user = new AccesoDispositivoService.Identidad(Long.MAX_VALUE, a.profile());
        when(access.perfilActivo(user)).thenReturn(new PerfilResumen(a.profile(), "Push test", null));
        // The earlier event is real persisted help. Preserve the 60-second rule;
        // advance this test clock from its database-generated timestamp.
        var previous = Objects.requireNonNull(tx.execute(status -> em.createQuery(
            "select max(s.fechaSolicitud) from SolicitudAyuda s where s.perfilUsuario.idPerfilUsuario=:id",
            LocalDateTime.class).setParameter("id", a.profile()).getSingleResult()));
        var lima = ZoneId.of("America/Lima");
        var premature = proxy(new SolicitudAyudaService(em, access,
            Clock.fixed(previous.plusSeconds(59).atZone(lima).toInstant(), lima)));
        assertEquals(429, assertThrows(dia_facil.backend.service.ayuda.RechazoAyuda.class,
            () -> premature.crear(user, new SolicitudAyudaService.Peticion(key(), null, null, null))).status());
        var target = new SolicitudAyudaService(em, access,
            Clock.fixed(previous.plusSeconds(61).atZone(lima).toInstant(), lima));
        target.setEventos(events);
        var service = proxy(target);
        var body = new SolicitudAyudaService.Peticion(key(), null, null, null);
        var created = service.crear(user, body);
        assertEquals(created.idSolicitudAyuda(), service.crear(user, body).idSolicitudAyuda());
        assertEquals(before + 1, count("NotificacionCuidador", a));
        assertEquals(1, deliveries().size());
        tx.executeWithoutResult(s -> {
            var delivery = em.find(EntregaPush.class, deliveries().getFirst().id());
            assertEquals(a.owner(), delivery.getToken().getCuidador().getIdCuidador());
            assertEquals(a.owner(), delivery.getNotificacion().getPerfilUsuario().getCuidador().getIdCuidador());
        });
    }

    @Test void dosWorkersUnSoloEnvioYReceiptSinMarcarLeida() throws Exception {
        register(a, UUID.randomUUID(), token()); int notification = event(a);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var expo = mock(ExpoPushClient.class);
        when(expo.enviar(anyMap())).thenAnswer(call -> {
            entered.countDown(); await(release);
            return new ExpoPushClient.Resultado("ticket-test", null, false, false);
        });
        when(expo.receipt("ticket-test")).thenReturn(new ExpoPushClient.Resultado("ticket-test", null, false, false));
        var pool = Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> worker(expo).procesar()); await(entered);
            worker(expo).procesar(); // The first lease is still active and the HTTP transaction is not open.
            verify(expo, times(1)).enviar(anyMap());
            release.countDown(); first.get(30, TimeUnit.SECONDS);
            var sent = deliveries().getFirst();
            assertEquals("ACEPTADA_EXPO", sent.state()); assertEquals(1, sent.attempts());
            due(sent.id()); worker(expo).procesar();
            assertEquals("ACEPTADA_PROVEEDOR", deliveries().getFirst().state());
            verify(expo, times(1)).receipt("ticket-test"); verify(expo, times(1)).enviar(anyMap());
            assertEquals(Boolean.FALSE, tx.<Boolean>execute(s -> em.find(NotificacionCuidador.class, notification).getLeida()));
        } finally {
            release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test void receiptAntiguoNoDesactivaGeneracionNueva() throws Exception {
        UUID install = UUID.randomUUID(); var original = register(a, install, token()); event(a);
        var expo = mock(ExpoPushClient.class);
        when(expo.enviar(anyMap())).thenReturn(new ExpoPushClient.Resultado("ticket-old", null, false, false));
        worker(expo).procesar(); due(deliveries().getFirst().id());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(expo.receipt("ticket-old")).thenAnswer(call -> {
            entered.countDown(); await(release);
            return new ExpoPushClient.Resultado(null, "DeviceNotRegistered", false, false);
        });
        var pool = Executors.newSingleThreadExecutor();
        try {
            var pending = pool.submit(() -> worker(expo).procesar()); await(entered);
            var renewed = register(a, install, token());
            assertTrue(renewed.generacion() > original.generacion());
            release.countDown(); pending.get(30, TimeUnit.SECONDS);
            assertEquals(renewed, registrations.consultar(a.identity(), install).estado());
            assertEquals("CANCELADA", deliveries().getFirst().state());
        } finally {
            release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test void cierreSesionCancelaPendientesYLimpiezaFkPermiteBorrarSesion() {
        UUID install = UUID.randomUUID(); var registered = register(a, install, token()); event(a);
        sessions.cerrar(a.refresh());
        var expo = mock(ExpoPushClient.class); worker(expo).procesar(); verifyNoInteractions(expo);
        assertEquals("CANCELADA", deliveries().getFirst().state());
        // Age our revoked fixture only; call the actual session cleanup implementation.
        tx.executeWithoutResult(s -> em.find(SesionCuidador.class, a.session()).setRevocadaEn(now().minusDays(8)));
        sessions.limpiarFinalizadas();
        tx.executeWithoutResult(s -> {
            assertNull(em.find(SesionCuidador.class, a.session()));
            var persisted = em.find(TokenPush.class, registered.idRegistro());
            assertNotNull(persisted); assertNull(persisted.getSesion(), "FK must use ON DELETE SET NULL");
        });
        event(a); assertEquals(1, deliveries().size(), "Orphaned registrations cannot receive new jobs");
    }

    @Test void ayudaGeneralContextualYFinalizacionUsanEventosRealesSinDuplicarNiAvanzarPorAyuda() {
        register(a, UUID.randomUUID(), token()); register(b, UUID.randomUUID(), token());
        var ids=Objects.requireNonNull(tx.execute(status->{
            var activity=Actividad.crear(em.find(PerfilUsuario.class,a.profile()),"Desayunar",LocalTime.NOON,null);em.persist(activity);
            var first=PasoActividad.crear(activity,1,"Primero",null);em.persist(first);
            var last=PasoActividad.crear(activity,2,"Segundo",null);em.persist(last);em.flush();
            return List.of(activity.getIdActividad(),first.getIdPasoActividad(),last.getIdPasoActividad());
        }));
        var access=mock(AccesoDispositivoService.class);
        var userA=new AccesoDispositivoService.Identidad(Long.MAX_VALUE,a.profile());
        var userB=new AccesoDispositivoService.Identidad(Long.MAX_VALUE-1,b.profile());
        when(access.perfilActivo(userA)).thenReturn(new PerfilResumen(a.profile(),"Push test",null));
        when(access.perfilActivo(userB)).thenReturn(new PerfilResumen(b.profile(),"Push test",null));
        var lima=ZoneId.of("America/Lima");var clock=Clock.fixed(Instant.now(),lima);
        var helpTarget=new SolicitudAyudaService(em,access,clock);helpTarget.setEventos(events);var help=proxy(helpTarget);
        var guideTarget=new dia_facil.backend.service.actividades.EjecucionUsuarioService(em,access,clock);
        guideTarget.setEventos(events);var guide=proxy(guideTarget);
        var general=new SolicitudAyudaService.Peticion(key(),null,null,null);
        var requested=help.crear(userB,general);assertEquals(requested.idSolicitudAyuda(),help.crear(userB,general).idSolicitudAyuda());
        var initial=guide.iniciar(userA,ids.get(0),LocalDate.now(clock));
        var context=new SolicitudAyudaService.Peticion(key(),ids.get(0),initial.idEjecucion(),ids.get(1));
        var contextual=help.crear(userA,context);assertEquals(1,contextual.posicionPaso());assertEquals("Primero",contextual.descripcionPaso());
        assertEquals(initial,guide.consultar(userA,ids.get(0),initial.idEjecucion()),"Help cannot advance or complete execution");
        assertEquals(contextual.idSolicitudAyuda(),help.crear(userA,context).idSolicitudAyuda());
        guide.avanzar(userA,ids.get(0),initial.idEjecucion(),ids.get(1));
        var completed=guide.finalizarGuiada(userA,ids.get(0),initial.idEjecucion(),ids.get(2));
        assertEquals(completed,guide.finalizarGuiada(userA,ids.get(0),initial.idEjecucion(),ids.get(2)));
        assertEquals(3,deliveries().size());assertEquals(3,count("NotificacionCuidador",a)+count("NotificacionCuidador",b));
        assertEquals(2,count("SolicitudAyuda",a)+count("SolicitudAyuda",b));
        var payloads=new ArrayList<Map<String,Object>>();var expo=mock(ExpoPushClient.class);
        when(expo.enviar(anyMap())).thenAnswer(call->{Map<String,Object> payload=call.getArgument(0);payloads.add(payload);return new ExpoPushClient.Resultado("ticket-"+payloads.size(),null,false,false);});
        worker(expo).procesar();assertEquals(3,payloads.size());
        assertEquals(1,payloads.stream().filter(p->"Push test necesita tu ayuda".equals(p.get("body"))).count());
        assertEquals(1,payloads.stream().filter(p->String.valueOf(p.get("body")).contains("paso 1 de Desayunar")).count());
        assertEquals(1,payloads.stream().filter(p->"Actividad completada".equals(p.get("title"))).count());
        assertEquals(2,payloads.stream().filter(p->Objects.equals(((Map<?,?>)p.get("data")).get("idCuidador"),a.owner())).count());
        assertEquals(1,payloads.stream().filter(p->Objects.equals(((Map<?,?>)p.get("data")).get("idCuidador"),b.owner())).count());
        assertTrue(deliveries().stream().allMatch(d->"ACEPTADA_EXPO".equals(d.state())));
        assertEquals(Long.valueOf(3),tx.<Long>execute(status->em.createQuery("select count(n) from NotificacionCuidador n where n.leida=false",Long.class).getSingleResult()));
    }
    private String revision(Fixture f, UUID id) { return registrations.consultar(f.identity(), id).etag(); }
    private RegistroPushService.Versionado put(Fixture f, UUID id, String value, String tag) {
        return registrations.registrar(f.identity(), new RegistroPushService.Peticion(id, value, "ANDROID"), tag);
    }
    private void stale(Runnable operation) {
        assertEquals(412, assertThrows(ResponseStatusException.class, operation::run).getStatusCode().value());
    }
    @Test void b1DeleteSinTokenEsPersistenteYRechazaPutAntiguoYRepetido() {
        UUID id=UUID.randomUUID(); String before=revision(a,id);
        assertFalse(registrations.consultar(a.identity(),id).estado().activo());
        assertEquals(Long.valueOf(0),tx.<Long>execute(s->em.createQuery(
            "select count(c) from ControlRegistroPush c where c.idInstalacion=:id",Long.class)
            .setParameter("id",id.toString()).getSingleResult()),"GET never creates a control row");
        String after=registrations.eliminar(a.identity(),id,before);
        assertNotEquals(before,after); assertEquals(after,revision(a,id));
        stale(()->put(a,id,token(),before));
        stale(()->registrations.eliminar(a.identity(),id,before));
        assertFalse(registrations.consultar(a.identity(),id).estado().activo());
        assertEquals(Long.valueOf(0),tx.<Long>execute(s->em.createQuery(
            "select count(t) from TokenPush t where t.idInstalacion=:id",Long.class)
            .setParameter("id",id.toString()).getSingleResult()));
        assertEquals(1L,tx.<Long>execute(s->em.find(ControlRegistroPush.class,id.toString()).getVersion()));
    }
    @Test void b1TokenExistenteDesactivadoNoSeReactivaConPutTardio() {
        UUID id=UUID.randomUUID(); register(a,id,token()); String old=revision(a,id);
        var generation=registrations.consultar(a.identity(),id).estado().generacion();
        registrations.eliminar(a.identity(),id,old);
        stale(()->put(a,id,token(),old));
        var inactive=registrations.consultar(a.identity(),id);
        assertFalse(inactive.estado().activo()); assertTrue(inactive.estado().generacion()>generation);
        registrations.eliminar(a.identity(),id,inactive.etag());
        assertEquals(inactive.estado().generacion(),registrations.consultar(a.identity(),id).estado().generacion(),
            "An ordering-only DELETE does not invalidate delivery generation again");
        event(a); assertTrue(deliveries().isEmpty()); assertEquals(1,count("NotificacionCuidador",a));
    }
    @Test void b1PutGanaYDeleteDebeReconciliarSinCambiarLaIntencion() {
        UUID id=UUID.randomUUID(); String initial=revision(a,id);
        var accepted=put(a,id,token(),initial);
        stale(()->registrations.eliminar(a.identity(),id,initial));
        registrations.eliminar(a.identity(),id,accepted.etag());
        stale(()->put(a,id,token(),initial));
        assertFalse(registrations.consultar(a.identity(),id).estado().activo());
    }
    private int outcome(Runnable operation) {
        try { operation.run(); return 200; }
        catch(ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
    @Test void b1PrimerasEscriturasConcurrentesSonAtomicasYSinDuplicados() throws Exception {
        for(boolean delete: List.of(false,true)) {
            UUID id=UUID.randomUUID(); String tag=revision(a,id); var start=new CountDownLatch(1);
            var pool=Executors.newFixedThreadPool(2);
            try {
                var first=pool.submit(()->{await(start);return outcome(()->put(a,id,token(),tag));});
                var second=pool.submit(()->{await(start);return outcome(()->{
                    if(delete) registrations.eliminar(a.identity(),id,tag); else put(a,id,token(),tag);
                });});
                start.countDown();
                var results=new ArrayList<>(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));
                Collections.sort(results); assertEquals(List.of(200,412),results);
                registrations.eliminar(a.identity(),id,revision(a,id));
                stale(()->put(a,id,token(),tag));
                assertFalse(registrations.consultar(a.identity(),id).estado().activo());
                assertTrue(tx.<Long>execute(s->em.createQuery(
                    "select count(t) from TokenPush t where t.idInstalacion=:id",Long.class)
                    .setParameter("id",id.toString()).getSingleResult())<=1);
            } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
        }
    }
    @Test void b1ControlSobreviveCambioDeTokenYNoPermiteReutilizarVersionDeOtraSesion() {
        UUID id=UUID.randomUUID(),other=UUID.randomUUID(); String value=token();
        register(a,id,value); String old=revision(a,id);
        var moved=register(a,other,value); // Token reuse must not delete the original installation fence.
        stale(()->put(a,id,token(),RegistroPushService.etag(id,a.session(),0)));
        assertNotEquals(old,revision(b,id));
        stale(()->put(b,id,token(),old));
        registrations.eliminar(a.identity(),id,old);
        stale(()->put(a,id,token(),old));
        assertEquals(moved,registrations.consultar(a.identity(),other).estado());
    }
    @Test void b1SesionRevocadaNoPuedeUsarUnaPrecondicionAntesValida() {
        UUID id=UUID.randomUUID(); String old=revision(a,id); sessions.cerrar(a.refresh());
        assertEquals(401,assertThrows(ResponseStatusException.class,()->put(a,id,token(),old)).getStatusCode().value());
    }

    @Test void b1UnaNuevaFabricaDePersistenciaConservaLaBarreraSinEstadoEnMemoria() {
        UUID id=UUID.randomUUID(); String old=revision(a,id);
        String disabled=registrations.eliminar(a.identity(),id,old);
        var ds=new DriverManagerDataSource(System.getenv("DIAFACIL_TEST_MYSQL_URL"),
            System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        var freshRegistry=new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource",ds)
            .applySetting("hibernate.hbm2ddl.auto","none").build();
        try {
            var sources=new MetadataSources(freshRegistry);
            for(String name:ENTITIES) try {sources.addAnnotatedClass(Class.forName("dia_facil.backend.persistence.entity."+name));}
                catch(ClassNotFoundException ex){throw new IllegalStateException(ex);}
            try(var freshFactory=sources.buildMetadata().buildSessionFactory()) {
                var manager=new JpaTransactionManager(freshFactory);manager.setJpaDialect(new HibernateJpaDialect());
                var target=new RegistroPushService(SharedEntityManagerCreator.createSharedEntityManager(freshFactory),new PushProperties(true,PROJECT,null));
                var p=new ProxyFactory(target);p.setProxyTargetClass(true);
                p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
                var restarted=(RegistroPushService)p.getProxy();
                assertEquals(disabled,restarted.consultar(a.identity(),id).etag());
                stale(()->restarted.registrar(a.identity(),new RegistroPushService.Peticion(id,token(),"ANDROID"),old));
                assertFalse(restarted.consultar(a.identity(),id).estado().activo());
            }
        } finally {StandardServiceRegistryBuilder.destroy(freshRegistry);}
    }

}
