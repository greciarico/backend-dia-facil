package dia_facil.backend.service.push;
import dia_facil.backend.persistence.entity.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service public class NotificacionEventoService {
 private static final int DELIVERY_LIFETIME_HOURS=24;
 private final EntityManager em;private final PushProperties config;
 public NotificacionEventoService(EntityManager em,PushProperties config){this.em=em;this.config=config;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void registrarNotificacion(NotificacionCuidador n){
  em.persist(n);if(!config.enabled())return;
  var now=LocalDateTime.now(ZoneOffset.UTC);
  var tokens=em.createQuery("select t from TokenPush t join fetch t.sesion s join fetch t.cuidador where t.activo=true and t.cuidador.idCuidador=:cuidador and t.proyectoEas=:proyecto",TokenPush.class).setParameter("cuidador",n.getPerfilUsuario().getCuidador().getIdCuidador()).setParameter("proyecto",config.projectId()).getResultList();
  for(var t:tokens)if(RegistroPushService.vigente(t.getSesion(),now)){var e=new EntregaPush();e.setNotificacion(n);e.setToken(t);e.setGeneracion(t.getGeneracion());e.setProximaEn(now);e.setVenceEn(now.plusHours(DELIVERY_LIFETIME_HOURS));em.persist(e);}
 }
}
