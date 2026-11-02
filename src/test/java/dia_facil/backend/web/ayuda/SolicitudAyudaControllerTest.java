package dia_facil.backend.web.ayuda;
import dia_facil.backend.configuration.*;
import dia_facil.backend.service.ayuda.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.web.error.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=SolicitudAyudaController.class,properties="diafacil.dispositivos.enabled=true")
@Import({SecurityConfiguration.class,DispositivoSecurityConfiguration.class,AyudaExceptionHandler.class,AutenticacionExceptionHandler.class})
class SolicitudAyudaControllerTest {
 @Autowired MockMvc mvc; @MockitoBean AccesoDispositivoService acceso;@MockitoBean SolicitudAyudaService service;
 final String token="du_"+"D".repeat(43),base="/api/usuario/solicitudes-ayuda",clave="c3c155b2-f8b5-4dba-8cb1-47ab9c6df806";
 final AccesoDispositivoService.Identidad principal=new AccesoDispositivoService.Identidad(1L,12);
 void auth(){when(acceso.autenticar(token)).thenReturn(principal);}
 @Test void postYConsultaUsanPrincipalSinCsrfYFechasConOffset() throws Exception {
  auth();var body=new SolicitudAyudaService.Peticion(clave,null,null,null);
  var dto=new SolicitudAyudaService.Ayuda(1,clave,OffsetDateTime.parse("2026-11-01T07:00:00-05:00"),"PENDIENTE",null,null,null,null,null);
  when(service.crear(principal,body)).thenReturn(dto);when(service.consultar(principal,clave)).thenReturn(dto);
  mvc.perform(post(base).header("Authorization","Bearer "+token).contentType("application/json").content("{\"claveOperacion\":\""+clave+"\",\"idPerfilUsuario\":999}"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.idSolicitudAyuda").value(1)).andExpect(jsonPath("$.fechaSolicitud").value("2026-11-01T07:00:00-05:00"));
  mvc.perform(get(base+"/por-clave/"+clave).header("Authorization","Bearer "+token)).andExpect(status().isOk());verify(service).crear(principal,body);
 }
 @Test void cooldownRetornaRetryAfterYProblema() throws Exception {auth();when(service.crear(eq(principal),any())).thenThrow(new RechazoAyuda(429,"AYUDA_PREMATURA","Espera",42));mvc.perform(post(base).header("Authorization","Bearer "+token).contentType("application/json").content("{}" )).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","42")).andExpect(jsonPath("$.codigo").value("AYUDA_PREMATURA"));}
 @Test void credencialPendienteNoPuedePedirAyuda() throws Exception {when(acceso.autenticar(token)).thenReturn(new AccesoDispositivoService.Identidad(1L,null));mvc.perform(post(base).header("Authorization","Bearer "+token).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void sinCredencialNoAccede() throws Exception {mvc.perform(get(base+"/por-clave/"+clave)).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
}
