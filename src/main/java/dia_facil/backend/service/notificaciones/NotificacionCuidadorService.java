package dia_facil.backend.service.notificaciones;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.TipoNotificacion;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import dia_facil.backend.service.ayuda.RechazoAyuda;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class NotificacionCuidadorService {
    private static final int PAGE_SIZE=20;
    private final EntityManager em;
    public NotificacionCuidadorService(EntityManager em){this.em=em;}
    public record Notificacion(Integer idNotificacion,String tipo,String titulo,String mensaje,java.time.OffsetDateTime fechaCreacion,boolean leida,String nombrePerfil,String codigoIcono,Integer idSolicitudAyuda,Integer idEjecucion){}
    public record Listado(List<Notificacion> notificaciones,String siguienteCursor){}
    private record Cursor(Integer cuidador,String categoria,LocalDateTime fecha,int id){}
    @Transactional(readOnly=true)
    public Listado listar(Identidad principal,String categoria,String cursor){
        int cuidador=id(principal);String cat=categoria==null?"TODAS":categoria;
        if(!Set.of("TODAS","AYUDA","SISTEMA").contains(cat))throw RechazoAyuda.datos();
        var c=cursor==null?null:decodificar(cursor,cuidador,cat);
        String filtro=switch(cat){case "AYUDA"->" and n.tipo in (:general,:contextual)";case "SISTEMA"->" and n.tipo=:sistema";default->"";};
        var q=em.createQuery("select n from NotificacionCuidador n where n.perfilUsuario.cuidador.idCuidador=:cuidador"+filtro+(c==null?"":" and (n.fechaCreacion<:fecha or (n.fechaCreacion=:fecha and n.idNotificacionCuidador<:id))")+" order by n.fechaCreacion desc,n.idNotificacionCuidador desc",NotificacionCuidador.class).setParameter("cuidador",cuidador).setMaxResults(PAGE_SIZE+1);
        if(cat.equals("AYUDA"))q.setParameter("general",TipoNotificacion.AYUDA_GENERAL).setParameter("contextual",TipoNotificacion.AYUDA_CONTEXTUAL);
        if(cat.equals("SISTEMA"))q.setParameter("sistema",TipoNotificacion.ACTIVIDAD_COMPLETADA);
        if(c!=null)q.setParameter("fecha",c.fecha()).setParameter("id",c.id());
        var rows=q.getResultList(); var pagina=rows.subList(0,Math.min(PAGE_SIZE,rows.size()));
        String siguiente=null;if(rows.size()>PAGE_SIZE){var ultima=pagina.getLast();siguiente=codificar(new Cursor(cuidador,cat,ultima.getFechaCreacion(),ultima.getIdNotificacionCuidador()));}
        return new Listado(pagina.stream().map(this::dto).toList(),siguiente);
    }
    @Transactional(readOnly=true)
    public Notificacion consultar(Identidad principal,Integer id){return dto(propia(principal,id));}
    @Transactional
    public Notificacion leer(Identidad principal,Integer id){var n=propia(principal,id);n.setLeida(true);return dto(n);}
    private int id(Identidad principal){if(principal==null||principal.cuidador()==null)throw new RechazoAyuda(401,"SESION_REQUIERE_RENOVACION","Verifica nuevamente tu acceso.",0);return principal.cuidador().idCuidador();}
    private NotificacionCuidador propia(Identidad principal,Integer id){int cuidador=id(principal);return em.createQuery("select n from NotificacionCuidador n where n.idNotificacionCuidador=:id and n.perfilUsuario.cuidador.idCuidador=:cuidador",NotificacionCuidador.class).setParameter("id",id).setParameter("cuidador",cuidador).getResultStream().findFirst().orElseThrow(()->new RechazoAyuda(404,"NOTIFICACION_NO_ENCONTRADA","No encontramos esta notificación.",0));}
    private Notificacion dto(NotificacionCuidador n){
        var s=n.getSolicitudAyuda();var e=n.getEjecucionActividad()!=null?n.getEjecucionActividad():s==null?null:s.getEjecucionActividad();
        var a=e!=null?e.getActividad():s==null?null:s.getActividad();
        return new Notificacion(n.getIdNotificacionCuidador(),n.getTipo().name(),n.getTitulo(),n.getMensaje(),n.getFechaCreacion().atZone(java.time.ZoneId.of("America/Lima")).toOffsetDateTime(),Boolean.TRUE.equals(n.getLeida()),n.getPerfilUsuario().getNombre(),a==null||a.getIconoActividad()==null?null:a.getIconoActividad().getCodigo(),s==null?null:s.getIdSolicitudAyuda(),e==null?null:e.getIdEjecucionActividad());
    }
    private static String codificar(Cursor c){return Base64.getUrlEncoder().withoutPadding().encodeToString((c.cuidador()+"|"+c.categoria()+"|"+c.fecha()+"|"+c.id()).getBytes(StandardCharsets.UTF_8));}
    private static Cursor decodificar(String raw,int cuidador,String categoria){try{
        if(raw.length()>256)throw RechazoAyuda.datos();var p=new String(Base64.getUrlDecoder().decode(raw),StandardCharsets.UTF_8).split("\\|");
        if(p.length!=4)throw RechazoAyuda.datos();var c=new Cursor(Integer.valueOf(p[0]),p[1],LocalDateTime.parse(p[2]),Integer.parseInt(p[3]));
        if(c.cuidador()!=cuidador||!c.categoria().equals(categoria)||c.id()<1)throw RechazoAyuda.datos();return c;
    }catch(IllegalArgumentException|java.time.format.DateTimeParseException ex){throw RechazoAyuda.datos();}}
}
