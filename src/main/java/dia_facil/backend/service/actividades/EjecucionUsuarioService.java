package dia_facil.backend.service.actividades;

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
public class EjecucionUsuarioService {
    private dia_facil.backend.service.push.NotificacionEventoService eventos;
    @org.springframework.beans.factory.annotation.Autowired
    public void setEventos(dia_facil.backend.service.push.NotificacionEventoService eventos){this.eventos=eventos;}
    private void registrarNotificacion(NotificacionCuidador n){if(eventos!=null)eventos.registrarNotificacion(n);else em.persist(n);}
    private final EntityManager em;
    private final AccesoDispositivoService acceso;
    private final Clock clock;
    public EjecucionUsuarioService(EntityManager em,AccesoDispositivoService acceso,@Qualifier("agendaUsuarioClock") Clock clock){this.em=em;this.acceso=acceso;this.clock=clock;}
    public record ActividadInfo(Integer idActividad,String nombre,boolean guiaInicialCompletada){}
    public record Paso(Integer idPasoActividad,int posicion,String descripcion,PlantillaActividadService.Pictograma pictograma,boolean alcanzado,boolean completado){}
    public record Guia(LocalDate fecha,String zonaHoraria,long actualizarEnSegundos,boolean vigente,Integer idEjecucion,
        String estado,String modoFinalizacion,ActividadInfo actividad,List<Paso> pasos){}
    public record Finalizacion(LocalDate fecha,Integer idEjecucion,String estado,String modoFinalizacion,ActividadInfo actividad){}
    private record Contexto(Actividad actividad,ZonedDateTime ahora){}

    @Transactional(readOnly=true)
    public Guia consultar(Identidad principal,Integer actividad,Integer ejecucion) {
        var c=contexto(principal,actividad,false);
        var e=ejecucion==null?actual(c,false):porId(c,ejecucion,false);
        return guia(c,e);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Guia iniciar(Identidad principal,Integer actividad,LocalDate fecha) {
        var c=contexto(principal,actividad,true);dia(c,fecha);
        var recorrido=recorrido(c,null); // Validate the real definition before creating anything.
        var e=actual(c,true);
        if(e==null){e=EjecucionActividad.iniciar(c.actividad(),fecha);em.persist(e);}
        if(e.getEstado()!=EstadoEjecucion.COMPLETADA) {
            var r=recorrido(c,e);
            if(r.maximo<0)em.persist(ProgresoPaso.alcanzar(e,recorrido.pasos.getFirst()));
        }
        em.flush();return guia(c,e);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Guia avanzar(Identidad principal,Integer actividad,Integer ejecucion,Integer paso) {
        var c=contexto(principal,actividad,true);var e=porId(c,ejecucion,true);dia(c,e.getFecha());
        if(e.getEstado()==EstadoEjecucion.COMPLETADA)return guia(c,e);
        var r=recorrido(c,e);int posicion=r.posicion(paso);
        if(posicion>r.maximo||posicion==r.pasos.size()-1)throw RechazoGuia.paso();
        completar(r.progreso.get(paso),c);
        var siguiente=r.pasos.get(posicion+1);
        if(!r.progreso.containsKey(siguiente.getIdPasoActividad()))em.persist(ProgresoPaso.alcanzar(e,siguiente));
        em.flush();return guia(c,e);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Finalizacion finalizarGuiada(Identidad principal,Integer actividad,Integer ejecucion,Integer pasoActual) {
        var c=contexto(principal,actividad,true);var e=porId(c,ejecucion,true);dia(c,e.getFecha());
        if(e.getEstado()==EstadoEjecucion.COMPLETADA)return finalizacion(e);
        var r=recorrido(c,e);int posicion=r.posicion(pasoActual);
        if(posicion>r.maximo)throw RechazoGuia.paso();
        boolean ultimo=posicion==r.pasos.size()-1;
        if(!Boolean.TRUE.equals(c.actividad().getGuiaInicialCompletada())&&(!ultimo||!r.completoAntesDelUltimo()))throw RechazoGuia.inicial();
        if(ultimo){completar(r.progreso.get(pasoActual),c);c.actividad().setGuiaInicialCompletada(true);}
        cerrar(c,e,ModoFinalizacion.GUIADA);em.flush();return finalizacion(e);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Finalizacion finalizarDirecta(Identidad principal,Integer actividad,LocalDate fecha) {
        var c=contexto(principal,actividad,true);dia(c,fecha);var e=actual(c,true);
        if(e!=null&&e.getEstado()==EstadoEjecucion.COMPLETADA)return finalizacion(e);
        if(!Boolean.TRUE.equals(c.actividad().getGuiaInicialCompletada()))throw RechazoGuia.inicial();
        if(e==null){e=EjecucionActividad.iniciar(c.actividad(),fecha);em.persist(e);}
        cerrar(c,e,ModoFinalizacion.DIRECTA);em.flush();return finalizacion(e);
    }
    private Contexto contexto(Identidad principal,Integer id,boolean bloquear) {
        var perfil=acceso.perfilActivo(principal);
        if(bloquear) {
            var p=em.find(PerfilUsuario.class,perfil.idPerfilUsuario(),LockModeType.PESSIMISTIC_WRITE);
            if(p==null)throw RechazoActividad.noEncontrada();em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
            var cred=em.find(CredencialDispositivo.class,principal.idCredencial());
            if(cred!=null){em.refresh(cred);if(cred.getDispositivo()!=null)em.refresh(cred.getDispositivo());}
            if(!perfil.idPerfilUsuario().equals(acceso.perfilActivo(principal).idPerfilUsuario()))throw RechazoActividad.noEncontrada();
        }
        var a=id==null?null:em.find(Actividad.class,id);
        if(a==null||!perfil.idPerfilUsuario().equals(a.getPerfilUsuario().getIdPerfilUsuario()))throw RechazoActividad.noEncontrada();
        if(bloquear)em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
        if(!perfil.idPerfilUsuario().equals(a.getPerfilUsuario().getIdPerfilUsuario())||a.getEstado()!=EstadoActividad.ACTIVA||a.getPerfilUsuario().getEstado()!=EstadoPerfil.ACTIVO)throw RechazoActividad.noEncontrada();
        return new Contexto(a,ZonedDateTime.now(clock));
    }
    private EjecucionActividad actual(Contexto c,boolean bloquear) {
        var q=em.createQuery("select e from EjecucionActividad e where e.actividad.idActividad=:actividad and e.fecha=:fecha",EjecucionActividad.class)
            .setParameter("actividad",c.actividad().getIdActividad()).setParameter("fecha",c.ahora().toLocalDate());
        if(bloquear)q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        var e=q.getResultStream().findFirst().orElse(null);
        if(e!=null&&bloquear)em.refresh(e,LockModeType.PESSIMISTIC_WRITE);return e;
    }
    private EjecucionActividad porId(Contexto c,Integer id,boolean bloquear) {
        var e=id==null?null:em.find(EjecucionActividad.class,id);
        if(e==null||!c.actividad().getIdActividad().equals(e.getActividad().getIdActividad()))throw RechazoGuia.ejecucion();
        if(bloquear)em.refresh(e,LockModeType.PESSIMISTIC_WRITE);return e;
    }
    private RecorridoGuia recorrido(Contexto c,EjecucionActividad e) {
        var pasos=em.createQuery("select p from PasoActividad p left join fetch p.pictograma where p.actividad.idActividad=:actividad and p.activo=true order by p.orden,p.idPasoActividad",PasoActividad.class)
            .setParameter("actividad",c.actividad().getIdActividad()).getResultList();
        var progreso=e==null?List.<ProgresoPaso>of():em.createQuery("select p from ProgresoPaso p join fetch p.pasoActividad where p.ejecucionActividad.idEjecucionActividad=:ejecucion",ProgresoPaso.class)
            .setParameter("ejecucion",e.getIdEjecucionActividad()).getResultList();
        return new RecorridoGuia(pasos,progreso);
    }
    private Guia guia(Contexto c,EjecucionActividad e) {
        var r=recorrido(c,e);var pasos=new ArrayList<Paso>();
        for(int i=0;i<r.pasos.size();i++) {
            var p=r.pasos.get(i);var g=r.progreso.get(p.getIdPasoActividad());var pic=p.getPictograma();
            pasos.add(new Paso(p.getIdPasoActividad(),i+1,p.getDescripcion(),pic==null?null:PlantillaActividadService.Pictograma.de(pic.getIdPictograma(),pic.getNombre(),pic.getIdArasaac()),g!=null,g!=null&&Boolean.TRUE.equals(g.getCompletado())));
        }
        var fecha=e==null?c.ahora().toLocalDate():e.getFecha();
        long segundos=Math.max(1,(Duration.between(c.ahora().toInstant(),c.ahora().toLocalDate().plusDays(1).atStartOfDay(clock.getZone()).toInstant()).toMillis()+999)/1000);
        return new Guia(fecha,clock.getZone().getId(),segundos,fecha.equals(c.ahora().toLocalDate()),e==null?null:e.getIdEjecucionActividad(),
            e==null?"PENDIENTE":e.getEstado().name(),e==null||e.getModoFinalizacion()==null?null:e.getModoFinalizacion().name(),info(c.actividad()),List.copyOf(pasos));
    }
    private ActividadInfo info(Actividad a){return new ActividadInfo(a.getIdActividad(),a.getNombre(),Boolean.TRUE.equals(a.getGuiaInicialCompletada()));}
    private Finalizacion finalizacion(EjecucionActividad e){return new Finalizacion(e.getFecha(),e.getIdEjecucionActividad(),e.getEstado().name(),e.getModoFinalizacion().name(),info(e.getActividad()));}
    private void dia(Contexto c,LocalDate fecha){if(!c.ahora().toLocalDate().equals(fecha))throw RechazoGuia.dia();}
    private void completar(ProgresoPaso p,Contexto c){if(p==null)throw RechazoGuia.paso();if(!Boolean.TRUE.equals(p.getCompletado())){p.setCompletado(true);p.setFechaCompletado(c.ahora().toLocalDateTime());}}
    private void cerrar(Contexto c,EjecucionActividad e,ModoFinalizacion modo){e.setEstado(EstadoEjecucion.COMPLETADA);e.setModoFinalizacion(modo);e.setFechaFin(c.ahora().toLocalDateTime());registrarNotificacion(NotificacionCuidador.completada(e));}
}
