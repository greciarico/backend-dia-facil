package dia_facil.backend.service.push;
import dia_facil.backend.persistence.entity.*;
import dia_facil.backend.persistence.enums.EstadoCuidador;
import dia_facil.backend.service.auth.SesionCuidadorService.Identidad;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class PushUnitTest {
 final PushProperties config=new PushProperties(true,"a2e2af44-1234-4321-abcd-112233445566","test-secret");
 @Test void ticketsYReceiptsNoEquivalenAVisualizacion(){
  var b=RestClient.builder().baseUrl("https://exp.host/--/api/v2/push");var server=MockRestServiceServer.bindTo(b).build();var c=new ExpoPushClient(config,b.build());
  server.expect(requestTo("https://exp.host/--/api/v2/push/send")).andExpect(header("Authorization","Bearer test-secret")).andRespond(withSuccess("{\"data\":{\"status\":\"ok\",\"id\":\"ticket-1\"}}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://exp.host/--/api/v2/push/getReceipts")).andRespond(withSuccess("{\"data\":{\"ticket-1\":{\"status\":\"ok\"}}}",MediaType.APPLICATION_JSON));
  assertEquals("ticket-1",c.enviar(Map.of()).ticket());
  assertNull(c.receipt("ticket-1").error());server.verify();
 }
 @Test void receiptPendienteYTokenInvalido(){
  var builder=RestClient.builder().baseUrl("https://exp.host/--/api/v2/push");var server=MockRestServiceServer.bindTo(builder).build();var client=new ExpoPushClient(config,builder.build());
  server.expect(anything()).andRespond(withSuccess("{\"data\":{}}",MediaType.APPLICATION_JSON));
  server.expect(anything()).andRespond(withSuccess("{\"data\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}",MediaType.APPLICATION_JSON));
  assertEquals("RECEIPT_PENDIENTE",client.receipt("x").error());
  assertEquals("DeviceNotRegistered",client.enviar(Map.of()).error());server.verify();
 }
 @Test void retryAfterCredencialesYRespuestaInvalida(){
  var b=RestClient.builder().baseUrl("https://exp.host/--/api/v2/push");var server=MockRestServiceServer.bindTo(b).build();var c=new ExpoPushClient(config,b.build());
  server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","3600"));
  server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
  server.expect(anything()).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
  var r=c.enviar(Map.of());assertTrue(r.temporal());assertEquals(3600,r.retryAfter());assertTrue(c.enviar(Map.of()).credenciales());assertTrue(c.enviar(Map.of()).temporal());server.verify();
 }
 @Test void backoffRespetaLimiteYRetryAfter(){var now=LocalDateTime.now();assertEquals(30,EntregaPushWorker.espera(1));assertEquals(43200,EntregaPushWorker.espera(7));var next=EntregaPushWorker.siguiente(now,now.plusHours(24),1,3600);assertFalse(next.isBefore(now.plusHours(1)));assertFalse(next.isAfter(now.plusSeconds(3960)));assertEquals(now.plusSeconds(1),EntregaPushWorker.siguiente(now,now.plusSeconds(1),8,86400));}
 @Test void sesionExpiradaRevocadaOCuidadorInactivoNoSonElegibles(){var now=LocalDateTime.now();var c=Cuidador.crear("a@example.com","A");var s=SesionCuidador.crear(c,now,now.plusHours(1),now.plusDays(1));assertTrue(RegistroPushService.vigente(s,now));s.setRevocadaEn(now);assertFalse(RegistroPushService.vigente(s,now));s.setRevocadaEn(null);s.setInactividadHasta(now);assertFalse(RegistroPushService.vigente(s,now));s.setInactividadHasta(now.plusHours(1));c.setEstado(EstadoCuidador.INACTIVO);assertFalse(RegistroPushService.vigente(s,now));assertFalse(RegistroPushService.vigente(null,now));}
 @Test void pushDesactivadoSigueGuardandoHistorialSinCola(){var em=mock(EntityManager.class);var n=mock(NotificacionCuidador.class);new NotificacionEventoService(em,new PushProperties(false,null,null)).registrarNotificacion(n);verify(em).persist(n);verifyNoMoreInteractions(em);}
 @Test void validacionNoExponeToken(){var em=mock(EntityManager.class);var svc=new RegistroPushService(em,config);var b=new RegistroPushService.Peticion(UUID.randomUUID(),"secret","ANDROID");assertFalse(b.toString().contains("secret"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->svc.registrar(null,b,RegistroPushService.etag(b.idInstalacion(),1L,0)));verifyNoInteractions(em);}
 @Test void receiptsRespetanRetryAfterYExpiranSinReenviar(){
  var now=LocalDateTime.of(2026,11,1,0,0);var expires=now.plusHours(24);
  var normal=EntregaPushWorker.siguienteReceipt(now,expires,0);
  assertFalse(normal.isBefore(now.plusMinutes(15)));assertFalse(normal.isAfter(now.plusSeconds(990)));
  var limited=EntregaPushWorker.siguienteReceipt(now,expires,3600);
  assertFalse(limited.isBefore(now.plusHours(1)));assertEquals(now.plusMinutes(1),EntregaPushWorker.siguienteReceipt(now,now.plusMinutes(1),3600));
 }
 @Test void representacionesNoExponenCredencialesNiContenido(){
  var properties=new PushProperties(true,"project-fixture","private-token-fixture");
  assertFalse(properties.toString().contains("private-token-fixture"));assertTrue(properties.toString().contains("<redacted>"));
  var work=new EntregaPushWorker.Trabajo(7L,"private-lease","private-ticket",Map.of("to","private-destination","body","private-content"));
  var printed=work.toString();
  for(var reserved:List.of("private-lease","private-ticket","private-destination","private-content"))assertFalse(printed.contains(reserved));
  assertTrue(printed.contains("7"));assertTrue(printed.contains("<redacted>"));
 }
}
