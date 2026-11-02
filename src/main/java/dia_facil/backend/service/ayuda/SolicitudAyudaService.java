package dia_facil.backend.service.ayuda;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.*;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService;
import dia_facil.backend.service.dispositivos.AccesoDispositivoService.Identidad;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@ConditionalOnProperty(name="diafacil.dispositivos.enabled",havingValue="true")
public class SolicitudAyudaService {
    private static final int COOLDOWN_SECONDS=60;
    private dia_facil.backend.service.push.NotificacionEventoService eventos;
    @org.springframework.beans.factory.annotation.Autowired
    public void setEventos(dia_facil.backend.service.push.NotificacionEventoService eventos){this.eventos=eventos;}
    private void registrarNotificacion(NotificacionCuidador n){if(eventos!=null)eventos.registrarNotificacion(n);else em.persist(n);}
    private final EntityManager em; private final AccesoDispositivoService acceso; private final Clock clock;
    public SolicitudAyudaService(EntityManager em,AccesoDispositivoService acceso,@Qualifier("agendaUsuarioClock") Clock clock){this.em=em;this.acceso=acceso;this.clock=clock;}
    public record Peticion(String claveOperacion,Integer idActividad,Integer idEjecucion,Integer idPasoActividad){}
    public record Ayuda(Integer idSolicitudAyuda,String claveOperacion,OffsetDateTime fechaSolicitud,String estado,Integer idActividad,Integer idEjecucion,Integer idPasoActividad,Integer posicionPaso,String descripcionPaso){}
    public static String clave(String raw){try {if(raw==null||!raw.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw RechazoAyuda.datos(); return UUID.fromString(raw).toString();}catch(IllegalArgumentException ex){throw RechazoAyuda.datos();}}
    @Transactional(readOnly=true)
    public Ayuda consultar(Identidad principal,String raw){var perfil=acceso.perfilActivo(principal);return dto(buscar(perfil.idPerfilUsuario(),clave(raw)).orElseThrow(RechazoAyuda::noEncontrada));}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ayuda crear(Identidad principal,Peticion body){
        if(body==null)throw RechazoAyuda.datos(); String clave=clave(body.claveOperacion());
        var identidad=acceso.perfilActivo(principal);
        var perfil=em.find(PerfilUsuario.class,identidad.idPerfilUsuario(),LockModeType.PESSIMISTIC_WRITE);
        if(perfil==null)throw RechazoAyuda.noEncontrada(); em.refresh(perfil,LockModeType.PESSIMISTIC_WRITE);
        var cred=em.find(CredencialDispositivo.class,principal.idCredencial());
        if(cred!=null){em.refresh(cred);if(cred.getDispositivo()!=null)em.refresh(cred.getDispositivo());}
        if(!perfil.getIdPerfilUsuario().equals(acceso.perfilActivo(principal).idPerfilUsuario())||perfil.getEstado()!=EstadoPerfil.ACTIVO)throw RechazoAyuda.noEncontrada();
        var existente=buscar(perfil.getIdPerfilUsuario(),clave);if(existente.isPresent())return dto(existente.get());
        boolean general=body.idActividad()==null&&body.idEjecucion()==null&&body.idPasoActividad()==null;
        Actividad actividad=null; EjecucionActividad ejecucion=null; PasoActividad paso=null; Integer posicion=null;
        if(!general){
            if(body.idActividad()==null||body.idEjecucion()==null||body.idPasoActividad()==null)throw RechazoAyuda.datos();
            actividad=em.find(Actividad.class,body.idActividad());ejecucion=em.find(EjecucionActividad.class,body.idEjecucion());paso=em.find(PasoActividad.class,body.idPasoActividad());
            if(actividad==null||ejecucion==null||paso==null||!perfil.getIdPerfilUsuario().equals(actividad.getPerfilUsuario().getIdPerfilUsuario())||actividad.getEstado()!=EstadoActividad.ACTIVA||!body.idActividad().equals(ejecucion.getActividad().getIdActividad())||!body.idActividad().equals(paso.getActividad().getIdActividad())||!Boolean.TRUE.equals(paso.getActivo())||ejecucion.getEstado()!=EstadoEjecucion.EN_PROGRESO||!LocalDate.now(clock).equals(ejecucion.getFecha()))throw RechazoAyuda.contexto();
            var alcanzado=em.createQuery("select count(p) from ProgresoPaso p where p.ejecucionActividad.idEjecucionActividad=:ejecucion and p.pasoActividad.idPasoActividad=:paso",Long.class).setParameter("ejecucion",body.idEjecucion()).setParameter("paso",body.idPasoActividad()).getSingleResult();
            if(alcanzado==0)throw RechazoAyuda.contexto();
            var pasos=em.createQuery("select p from PasoActividad p where p.actividad.idActividad=:actividad and p.activo=true order by p.orden,p.idPasoActividad",PasoActividad.class).setParameter("actividad",body.idActividad()).getResultList();
            for(int i=0;i<pasos.size();i++)if(body.idPasoActividad().equals(pasos.get(i).getIdPasoActividad()))posicion=i+1;
            if(posicion==null)throw RechazoAyuda.contexto();
        }
        String filtro=general?"s.actividad is null and s.ejecucionActividad is null and s.pasoActividad is null":"s.ejecucionActividad.idEjecucionActividad=:ejecucion and s.pasoActividad.idPasoActividad=:paso";
        var q=em.createQuery("select s from SolicitudAyuda s where s.perfilUsuario.idPerfilUsuario=:perfil and "+filtro+" order by s.fechaSolicitud desc,s.idSolicitudAyuda desc",SolicitudAyuda.class).setParameter("perfil",perfil.getIdPerfilUsuario()).setMaxResults(1);
        if(!general)q.setParameter("ejecucion",body.idEjecucion()).setParameter("paso",body.idPasoActividad());
        var ultima=q.getResultStream().findFirst();
        if(ultima.isPresent()){
            long ms=Duration.between(LocalDateTime.now(clock),ultima.get().getFechaSolicitud().plusSeconds(COOLDOWN_SECONDS)).toMillis();
            if(ms>0)throw new RechazoAyuda(429,"AYUDA_PREMATURA","Espera antes de volver a pedir ayuda.",(ms+999)/1000);
        }
        var s=SolicitudAyuda.crear(perfil,clave,actividad,ejecucion,paso,posicion);em.persist(s);registrarNotificacion(NotificacionCuidador.ayuda(s));em.flush();return dto(s);
    }
    private Optional<SolicitudAyuda> buscar(Integer perfil,String clave){return em.createQuery("select s from SolicitudAyuda s where s.perfilUsuario.idPerfilUsuario=:perfil and s.claveOperacion=:clave",SolicitudAyuda.class).setParameter("perfil",perfil).setParameter("clave",clave).getResultStream().findFirst();}
    private Ayuda dto(SolicitudAyuda s){return new Ayuda(s.getIdSolicitudAyuda(),s.getClaveOperacion(),s.getFechaSolicitud().atZone(clock.getZone()).toOffsetDateTime(),s.getEstado().name(),s.getActividad()==null?null:s.getActividad().getIdActividad(),s.getEjecucionActividad()==null?null:s.getEjecucionActividad().getIdEjecucionActividad(),s.getPasoActividad()==null?null:s.getPasoActividad().getIdPasoActividad(),s.getPosicionPaso(),s.getDescripcionPaso());}
}
