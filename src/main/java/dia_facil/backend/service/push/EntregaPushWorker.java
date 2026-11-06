package dia_facil.backend.service.push;
import dia_facil.backend.persistence.entity.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="diafacil.push.enabled",havingValue="true")
public class EntregaPushWorker {
 private static final int MAX_WORK_PER_POLL=20, MAX_SEND_ATTEMPTS=8, LEASE_MINUTES=2, RECEIPT_DELAY_MINUTES=15;
 private static final long[] BACKOFF_SECONDS={30,120,600,1800,7200,21600,43200};
 private final EntityManager em;private final TransactionTemplate tx;private final ExpoPushClient expo;private final PushProperties config;
 private volatile boolean credencialesInvalidas;
 public EntregaPushWorker(EntityManager em,PlatformTransactionManager manager,ExpoPushClient expo,PushProperties config){this.em=em;this.expo=expo;this.config=config;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
 public record Trabajo(Long id,String lease,String ticket,Map<String,Object> payload){
  @Override public String toString(){return "Trabajo[id="+id+", contenido=<redacted>]";}
 }
 public static long espera(int intento){return BACKOFF_SECONDS[Math.min(Math.max(intento-1,0),BACKOFF_SECONDS.length-1)];}
 private LocalDateTime ahora(){return LocalDateTime.now(ZoneOffset.UTC);}
 @Scheduled(fixedDelayString="${diafacil.push.poll-ms:10000}")
 public void procesar(){if(credencialesInvalidas)return;for(int i=0;i<MAX_WORK_PER_POLL;i++){Trabajo t=tx.execute(status->reclamar());if(t==null)return;boolean valido=Boolean.TRUE.equals(tx.execute(status->revalidar(t)));if(!valido)continue;var resultado=t.ticket()==null?expo.enviar(t.payload()):expo.receipt(t.ticket());tx.executeWithoutResult(status->resultado(t,resultado));if(resultado.credenciales()){credencialesInvalidas=true;return;}}}
 private Trabajo reclamar(){
  var now=ahora();
  var lista=em.createQuery("select e from EntregaPush e where e.estado in ('PENDIENTE','ACEPTADA_EXPO','PROCESANDO') and e.proximaEn<=:now and (e.bloqueoHasta is null or e.bloqueoHasta<=:now) order by e.proximaEn,e.idEntrega",EntregaPush.class).setParameter("now",now).setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint("jakarta.persistence.lock.timeout",-2).setMaxResults(1).getResultList();
  if(lista.isEmpty())return null;var e=lista.getFirst();
  if(!e.getVenceEn().isAfter(now)){terminal(e,e.getTicket()==null?"EXPIRADA":"RESULTADO_DESCONOCIDO");return new Trabajo(e.getIdEntrega(),null,null,Map.of());}
  if(!elegible(e,now)){terminal(e,"CANCELADA");return new Trabajo(e.getIdEntrega(),null,null,Map.of());}
  if(e.getTicket()==null&&e.getIntentos()>=MAX_SEND_ATTEMPTS){terminal(e,"FALLIDA");return new Trabajo(e.getIdEntrega(),null,null,Map.of());}
  var lease=UUID.randomUUID().toString();e.setEstado("PROCESANDO");e.setArrendamiento(lease);e.setBloqueoHasta(now.plusMinutes(LEASE_MINUTES));
  if(e.getTicket()==null)e.setIntentos(e.getIntentos()+1);
  var n=e.getNotificacion();var t=e.getToken();
  var data=Map.<String,Object>of("version",1,"tipo","NOTIFICACION_CUIDADOR","idNotificacion",n.getIdNotificacionCuidador(),"idRegistro",t.getIdTokenPush(),"generacion",e.getGeneracion(),"idCuidador",t.getCuidador().getIdCuidador());
  var payload=Map.<String,Object>of("to",t.getExpoToken(),"title",n.getTitulo(),"body",n.getMensaje(),"sound","default","channelId","diafacil-cuidador-v1","ttl",Math.max(0,Duration.between(now,e.getVenceEn()).toSeconds()),"data",data);
  return new Trabajo(e.getIdEntrega(),lease,e.getTicket(),payload);
 }
 private boolean elegible(EntregaPush e,LocalDateTime now){var t=e.getToken();return t.isActivo()&&t.getGeneracion()==e.getGeneracion()&&Objects.equals(t.getProyectoEas(),config.projectId())&&RegistroPushService.vigente(t.getSesion(),now)&&t.getCuidador().getIdCuidador().equals(e.getNotificacion().getPerfilUsuario().getCuidador().getIdCuidador())&&t.getSesion().getCuidador().getIdCuidador().equals(t.getCuidador().getIdCuidador());}
 private EntregaPush propia(Trabajo t){if(t.lease()==null)return null;var e=em.find(EntregaPush.class,t.id(),LockModeType.PESSIMISTIC_WRITE);return e!=null&&t.lease().equals(e.getArrendamiento())?e:null;}
 private boolean revalidar(Trabajo t){var e=propia(t);if(e==null)return false;if(!elegible(e,ahora())||!e.getVenceEn().isAfter(ahora())){terminal(e,"CANCELADA");return false;}return true;}
 private void resultado(Trabajo t,ExpoPushClient.Resultado r){
  var e=propia(t);if(e==null)return;var now=ahora();
  em.lock(e.getToken(),LockModeType.PESSIMISTIC_WRITE);em.refresh(e.getToken(),LockModeType.PESSIMISTIC_WRITE);
  if(!elegible(e,now)){terminal(e,"CANCELADA");return;}
  e.setBloqueoHasta(null);e.setArrendamiento(null);e.setErrorCodigo(r.error());
  if("DeviceNotRegistered".equals(r.error())){e.getToken().setActivo(false);terminal(e,"FALLIDA");return;}
  if(r.error()==null){if(t.ticket()!=null){terminal(e,"ACEPTADA_PROVEEDOR");}else if(r.ticket()==null||r.ticket().isBlank()){terminal(e,"FALLIDA");e.setErrorCodigo("TICKET_INVALIDO");}else{e.setTicket(r.ticket());e.setEstado("ACEPTADA_EXPO");e.setProximaEn(now.plusMinutes(RECEIPT_DELAY_MINUTES).isAfter(e.getVenceEn())?e.getVenceEn():now.plusMinutes(RECEIPT_DELAY_MINUTES));}return;}
  if(r.credenciales()){terminal(e,"FALLIDA");return;}
  if(t.ticket()!=null&&r.temporal()){e.setEstado("ACEPTADA_EXPO");e.setProximaEn(siguienteReceipt(now,e.getVenceEn(),r.retryAfter()));return;}
  if(r.temporal()&&e.getIntentos()<MAX_SEND_ATTEMPTS&&e.getVenceEn().isAfter(now)){e.setEstado("PENDIENTE");e.setProximaEn(siguiente(now,e.getVenceEn(),e.getIntentos(),r.retryAfter()));return;}
  terminal(e,"FALLIDA");
 }
 public static LocalDateTime siguiente(LocalDateTime now,LocalDateTime vence,int intento,long retryAfter){long base=Math.max(espera(intento),Math.max(0,retryAfter));long jitter=java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1,base/10+1));var next=now.plusSeconds(base+jitter);return next.isAfter(vence)?vence:next;}
 public static LocalDateTime siguienteReceipt(LocalDateTime now,LocalDateTime vence,long retryAfter){return siguiente(now,vence,1,Math.max(RECEIPT_DELAY_MINUTES*60L,retryAfter));}
 private void terminal(EntregaPush e,String estado){e.setEstado(estado);e.setBloqueoHasta(null);e.setArrendamiento(null);}
}
