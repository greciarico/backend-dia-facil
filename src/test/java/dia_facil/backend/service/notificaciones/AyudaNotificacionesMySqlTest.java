package dia_facil.backend.service.notificaciones;

import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.service.actividades.EjecucionUsuarioService;
import dia_facil.backend.service.auth.SesionCuidadorService;
import dia_facil.backend.service.ayuda.*;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import dia_facil.backend.service.perfiles.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OPT-IN: externally prepared disposable replica, including Increment 1 migration.
 * No SpringBoot context, application-local properties, SMTP, DDL or schema creation.
 * Uses its own random owner; cleanup deletes only that owner. Never run against real data.
 */
@EnabledIfEnvironmentVariable(named="DIAFACIL_TEST_MYSQL_URL", matches=".+")
class AyudaNotificacionesMySqlTest {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private static final List<String> ENTITIES=List.of(
        "Cuidador","ConfiguracionCuidador","CodigoAcceso","PerfilUsuario","ConfiguracionPerfil",
        "Pictograma","IconoActividad","PalabraClaveIconoActividad","PalabraClavePictograma",
        "PlantillaActividad","PalabraClaveActividad","PlantillaPaso","Actividad","PasoActividad",
        "Dispositivo","CredencialDispositivo","EjecucionActividad","ProgresoPaso","SolicitudAyuda",
        "NotificacionCuidador","EntregaPush","TokenPush","SesionCuidador","CredencialSesionCuidador");

    @SuppressWarnings("unchecked")
    private static <T> T proxy(T target,JpaTransactionManager tm) {
        var p=new ProxyFactory(target);p.setProxyTargetClass(true);
        p.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource()));
        return (T)p.getProxy();
    }
    private static Clock at(LocalDateTime date) { return Clock.fixed(date.atZone(LIMA).toInstant(),LIMA); }
    private static SolicitudAyudaService.Peticion general(String key) {
        return new SolicitudAyudaService.Peticion(key,null,null,null);
    }
    private static String key() {return UUID.randomUUID().toString();}
    private static <T> List<T> parallel(Callable<T> a,Callable<T> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var x=pool.submit(()->{assertTrue(gate.await(10,TimeUnit.SECONDS));return a.call();});
            var y=pool.submit(()->{assertTrue(gate.await(10,TimeUnit.SECONDS));return b.call();});
            gate.countDown();return List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
    }
    @Test void concurrenciaCooldownProgresoRollbackYHistorial() throws Exception {
        String url=System.getenv("DIAFACIL_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/;]+/diafacil_test_[A-Za-z0-9_]+(?:\\?[^#]*)?"),"Disposable schema required");
        assertFalse(url.toLowerCase(Locale.ROOT).matches(".*(databaseterm|createdatabaseifnotexist|sessionvariables|initsql).*"));
        var ds=new DriverManagerDataSource(url,System.getenv("DIAFACIL_TEST_MYSQL_USER"),System.getenv("DIAFACIL_TEST_MYSQL_PASSWORD"));
        LocalDateTime dbNow;
        try(var c=ds.getConnection()) {
            String address=url.split("\\?",2)[0];
            String expected=address.substring(address.lastIndexOf('/')+1);
            assertEquals(expected,c.getCatalog());assertTrue(c.getCatalog().startsWith("diafacil_test_"));
            // Read only preflight; absent columns/constraints mean the replica is not prepared.
            try(var s=c.createStatement();var r=s.executeQuery("SELECT CURRENT_TIMESTAMP")){assertTrue(r.next());dbNow=r.getTimestamp(1).toLocalDateTime();}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT clave_operacion,posicion_paso,descripcion_paso FROM solicitud_ayuda WHERE 1=0")){assertFalse(r.next());}
            for(String index:List.of("uq_ayuda_perfil_operacion","uq_notif_solicitud","uq_notif_ejecucion")) {
                try(var s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name=? AND non_unique=0")) {
                    s.setString(1,index);try(var r=s.executeQuery()){assertTrue(r.next());assertTrue(r.getInt(1)>0,"Missing "+index);}
                }
            }
        }
        var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource",ds)
            .applySetting("hibernate.connection.handling_mode","DELAYED_ACQUISITION_AND_HOLD")
            .applySetting("hibernate.hbm2ddl.auto","none").build();
        try {
            var sources=new MetadataSources(registry);
            for(String name:ENTITIES)sources.addAnnotatedClass(Class.forName("dia_facil.backend.persistence.entity."+name));
            try(var factory=sources.buildMetadata().buildSessionFactory()) {
                var em=SharedEntityManagerCreator.createSharedEntityManager(factory);var tm=new JpaTransactionManager(factory);
                tm.setJpaDialect(new org.springframework.orm.jpa.vendor.HibernateJpaDialect());
                var tx=new TransactionTemplate(tm);
                var ids=Objects.requireNonNull(tx.execute(status->{
                    var owner=Cuidador.crear(key()+"@example.invalid","Test");em.persist(owner);
                    var profile=PerfilUsuario.crear(owner,"Test",null,new GeneradorCodigoPerfil().generar());em.persist(profile);
                    var activity=Actividad.crear(profile,"Actividad",LocalTime.NOON,null);em.persist(activity);
                    var p1=PasoActividad.crear(activity,1,"Primero",null);em.persist(p1);
                    var p2=PasoActividad.crear(activity,2,"Segundo",null);em.persist(p2);em.flush();
                    return List.of(owner.getIdCuidador(),profile.getIdPerfilUsuario(),activity.getIdActividad(),p1.getIdPasoActividad(),p2.getIdPasoActividad());
                }));
                try {
                    var access=mock(AccesoDispositivoService.class);
                    var user=new AccesoDispositivoService.Identidad(Long.MAX_VALUE,ids.get(1));
                    var user2=new AccesoDispositivoService.Identidad(Long.MAX_VALUE-1,ids.get(1));
                    when(access.perfilActivo(user)).thenReturn(new PerfilResumen(ids.get(1),"Test",null));
                    when(access.perfilActivo(user2)).thenReturn(new PerfilResumen(ids.get(1),"Test",null));
                    var clock=at(dbNow);
                    var help=proxy(new SolicitudAyudaService(em,access,clock),tm);
                    var guide=proxy(new EjecucionUsuarioService(em,access,clock),tm);
                    var notifications=proxy(new NotificacionCuidadorService(em),tm);
                    var carer=new SesionCuidadorService.Identidad(1L,new SesionCuidadorService.CuidadorResumen(ids.get(0),"Test",null));
                    Supplier<Long> helps=()->tx.execute(s->em.createQuery("select count(s) from SolicitudAyuda s where s.perfilUsuario.idPerfilUsuario=:id",Long.class).setParameter("id",ids.get(1)).getSingleResult());
                    Supplier<Long> notices=()->tx.execute(s->em.createQuery("select count(n) from NotificacionCuidador n where n.perfilUsuario.idPerfilUsuario=:id",Long.class).setParameter("id",ids.get(1)).getSingleResult());
                    String first=key();
                    var same=parallel(()->help.crear(user,general(first)),()->help.crear(user2,general(first)));
                    assertEquals(same.getFirst().idSolicitudAyuda(),same.getLast().idSolicitudAyuda());
                    assertEquals(1L,helps.get());assertEquals(1L,notices.get());
                    assertEquals(same.getFirst().idSolicitudAyuda(),help.consultar(user,first).idSolicitudAyuda());

                    LocalDateTime inserted=same.getFirst().fechaSolicitud().toLocalDateTime();
                    var before=proxy(new SolicitudAyudaService(em,access,at(inserted.plusSeconds(59))),tm);
                    assertEquals(429,assertThrows(RechazoAyuda.class,()->before.crear(user,general(key()))).status());
                    // Age only our fixture: a fixed future Clock cannot govern MySQL-generated timestamps.
                    tx.executeWithoutResult(s->em.createNativeQuery("UPDATE solicitud_ayuda SET fecha_solicitud=:fecha WHERE id_solicitud_ayuda=:id")
                        .setParameter("fecha",inserted.minusSeconds(60)).setParameter("id",same.getFirst().idSolicitudAyuda()).executeUpdate());
                    var boundary=proxy(new SolicitudAyudaService(em,access,at(inserted)),tm);
                    // Two *different* keys at boundary: exactly one wins, the other must see committed cooldown.
                    Callable<Integer> sendA=()->{try{boundary.crear(user,general(key()));return 201;}catch(RechazoAyuda e){return e.status();}};
                    Callable<Integer> sendB=()->{try{boundary.crear(user2,general(key()));return 201;}catch(RechazoAyuda e){return e.status();}};
                    assertEquals(List.of(201,429),parallel(sendA,sendB).stream().sorted().toList());
                    assertEquals(2L,helps.get());assertEquals(2L,notices.get());

                    var initial=guide.iniciar(user,ids.get(2),dbNow.toLocalDate());
                    var context=new SolicitudAyudaService.Peticion(key(),ids.get(2),initial.idEjecucion(),ids.get(3));
                    var requested=help.crear(user,context);assertEquals(1,requested.posicionPaso());assertEquals("Primero",requested.descripcionPaso());
                    assertEquals(initial,guide.consultar(user,ids.get(2),initial.idEjecucion()),"Help must not mutate progress");
                    long count=helps.get();
                    var future=new SolicitudAyudaService.Peticion(key(),ids.get(2),initial.idEjecucion(),ids.get(4));
                    assertEquals(409,assertThrows(RechazoAyuda.class,()->help.crear(user,future)).status());
                    assertEquals(count,helps.get());
                    guide.avanzar(user,ids.get(2),initial.idEjecucion(),ids.get(3));
                    long preFinish=notices.get();
                    parallel(()->guide.finalizarGuiada(user,ids.get(2),initial.idEjecucion(),ids.get(4)),
                        ()->guide.finalizarGuiada(user2,ids.get(2),initial.idEjecucion(),ids.get(4)));
                    assertEquals(preFinish+1,notices.get(),"Exactly one completion event");
                    assertEquals(requested.idSolicitudAyuda(),help.crear(user,context).idSolicitudAyuda(),"Retry survives completed context");
                    assertEquals(409,assertThrows(RechazoAyuda.class,()->help.crear(user,new SolicitudAyudaService.Peticion(key(),ids.get(2),initial.idEjecucion(),ids.get(3)))).status());

                    long preRollback=helps.get(), preRollbackN=notices.get();
                    var broken=mock(EntityManager.class,org.mockito.AdditionalAnswers.delegatesTo(em));
                    doAnswer(call->{em.flush();throw new IllegalStateException("failure after insert");}).when(broken).persist(any(NotificacionCuidador.class));
                    var failing=proxy(new SolicitudAyudaService(broken,access,at(inserted.plusDays(1))),tm);
                    String failedKey=key();
                    assertThrows(IllegalStateException.class,()->failing.crear(user,general(failedKey)));
                    assertEquals(preRollback,helps.get());assertEquals(preRollbackN,notices.get());
                    assertEquals(404,assertThrows(RechazoAyuda.class,()->help.consultar(user,failedKey)).status());

                    // Direct fixtures bypass rate limiting solely to exercise 20-row pagination ties.
                    tx.executeWithoutResult(s->{
                        var profile=em.find(PerfilUsuario.class,ids.get(1));
                        for(int i=0;i<23;i++){var a=SolicitudAyuda.crear(profile,key(),null,null,null,null);em.persist(a);em.persist(NotificacionCuidador.ayuda(a));}
                        em.flush();
                        em.createNativeQuery("UPDATE notificacion_cuidador SET fecha_creacion=:fecha WHERE id_perfil_usuario=:id")
                            .setParameter("fecha",inserted).setParameter("id",ids.get(1)).executeUpdate();
                    });
                    var page=notifications.listar(carer,"TODAS",null);assertEquals(20,page.notificaciones().size());assertNotNull(page.siguienteCursor());
                    var seen=new HashSet<Integer>();page.notificaciones().forEach(n->assertTrue(seen.add(n.idNotificacion())));
                    var second=notifications.listar(carer,"TODAS",page.siguienteCursor());
                    second.notificaciones().forEach(n->assertTrue(seen.add(n.idNotificacion())));
                    assertNull(second.siguienteCursor());assertEquals(notices.get().intValue(),seen.size());
                    assertEquals(1,notifications.listar(carer,"SISTEMA",null).notificaciones().size());
                    assertTrue(notifications.listar(carer,"AYUDA",null).notificaciones().stream().noneMatch(n->n.tipo().equals("ACTIVIDAD_COMPLETADA")));
                    assertEquals(400,assertThrows(RechazoAyuda.class,()->notifications.listar(carer,"AYUDA",page.siguienteCursor())).status());
                    int readId=page.notificaciones().getFirst().idNotificacion();
                    assertTrue(notifications.leer(carer,readId).leida());assertTrue(notifications.leer(carer,readId).leida());
                    assertTrue(notifications.consultar(carer,readId).leida());
                    var other=new SesionCuidadorService.Identidad(2L,new SesionCuidadorService.CuidadorResumen(-1,"Other",null));
                    assertEquals(404,assertThrows(RechazoAyuda.class,()->notifications.leer(other,readId)).status());
                    assertTrue(notifications.listar(other,"TODAS",null).notificaciones().isEmpty());
                } finally {
                    tx.executeWithoutResult(s->em.createQuery("delete from Cuidador c where c.idCuidador=:id").setParameter("id",ids.getFirst()).executeUpdate());
                }
            }
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }
}
