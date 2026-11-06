package dia_facil.backend.service.push;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
@Component public class ExpoPushClient {
 private static final int CONNECT_TIMEOUT_SECONDS=5, READ_TIMEOUT_SECONDS=15;
 private static final long MAX_RETRY_AFTER_SECONDS=86400;
 public record Resultado(String ticket,String error,boolean temporal,boolean credenciales,long retryAfter){public Resultado(String ticket,String error,boolean temporal,boolean credenciales){this(ticket,error,temporal,credenciales,0);}}
 private final RestClient http;private final PushProperties config;
 @org.springframework.beans.factory.annotation.Autowired
 public ExpoPushClient(PushProperties config){this(config,crear());}
 public ExpoPushClient(PushProperties config,RestClient http){this.config=config;this.http=http;}
 private static RestClient crear(){var f=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build());f.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));return RestClient.builder().baseUrl("https://exp.host/--/api/v2/push").requestFactory(f).build();}
 private JsonNode post(String ruta,Object body){var req=http.post().uri(ruta).contentType(org.springframework.http.MediaType.APPLICATION_JSON);if(config.accessToken()!=null&&!config.accessToken().isBlank())req.header("Authorization","Bearer "+config.accessToken());return req.body(body).retrieve().body(JsonNode.class);}
 public Resultado enviar(Map<String,Object> payload){return ejecutar("/send",payload,null);}
 public Resultado receipt(String ticket){return ejecutar("/getReceipts",Map.of("ids",List.of(ticket)),ticket);}
 private Resultado ejecutar(String ruta,Object payload,String ticket){try {
  var root=post(ruta,payload);if(root==null||!root.hasNonNull("data")){boolean auth=root!=null&&root.path("errors").toString().contains("UNAUTHORIZED");return new Resultado(null,auth?"CREDENCIALES":"RESPUESTA_INVALIDA",!auth,auth);}
  var data=root.get("data");if(ticket!=null){if(!data.has(ticket))return new Resultado(null,"RECEIPT_PENDIENTE",true,false);data=data.get(ticket);}else if(data.isArray())data=data.size()==1?data.get(0):null;
  if(data==null||!data.isObject())return new Resultado(null,"RESPUESTA_INVALIDA",true,false);
  if("ok".equals(data.path("status").asText())){String id=ticket==null?data.path("id").asText(null):ticket;if(id==null||!id.matches("[A-Za-z0-9_-]{1,255}"))return new Resultado(null,"TICKET_INVALIDO",true,false);return new Resultado(id,null,false,false);}
  if(!"error".equals(data.path("status").asText()))return new Resultado(null,"RESPUESTA_INVALIDA",true,false);
  String error=data.path("details").path("error").asText("PAYLOAD_INVALIDO");
  return new Resultado(null,sanitizar(error),"MessageRateExceeded".equals(error),("InvalidCredentials".equals(error)||"MismatchSenderId".equals(error)));
 }catch(RestClientResponseException ex){int status=ex.getStatusCode().value();return new Resultado(null,"HTTP_"+status,status==429||status>=500,status==401||status==403,retryAfter(ex));}catch(org.springframework.web.client.RestClientException ex){return new Resultado(null,"RED",true,false);}}
 static long retryAfter(RestClientResponseException ex){try{String raw=ex.getResponseHeaders()==null?null:ex.getResponseHeaders().getFirst("Retry-After");if(raw==null)return 0;try{return Math.min(MAX_RETRY_AFTER_SECONDS,Math.max(0,Long.parseLong(raw)));}catch(NumberFormatException ignored){return Math.min(MAX_RETRY_AFTER_SECONDS,Math.max(0,java.time.Duration.between(java.time.Instant.now(),java.time.ZonedDateTime.parse(raw,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()));}}catch(RuntimeException ignored){return 0;}}
 private String sanitizar(String raw){return Set.of("DeviceNotRegistered","MessageTooBig","MessageRateExceeded","MismatchSenderId","InvalidCredentials").contains(raw)?raw:"PAYLOAD_INVALIDO";}
}
