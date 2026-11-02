package dia_facil.backend.web.notificaciones;
import dia_facil.backend.configuration.*;
import dia_facil.backend.service.auth.SesionCuidadorService;
import dia_facil.backend.service.notificaciones.NotificacionCuidadorService;
import dia_facil.backend.web.error.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=NotificacionCuidadorController.class)
@Import({SecurityConfiguration.class,AyudaExceptionHandler.class,AutenticacionExceptionHandler.class})
class NotificacionCuidadorControllerTest {
 @Autowired MockMvc mvc;@MockitoBean SesionCuidadorService sesiones;@MockitoBean NotificacionCuidadorService service;
 final String token="a_"+"A".repeat(43);final SesionCuidadorService.Identidad principal=new SesionCuidadorService.Identidad(1L,new SesionCuidadorService.CuidadorResumen(4,"Test",null));
 void auth(){when(sesiones.autenticar(token)).thenReturn(principal);}
 @Test void listadoYLeidaAutorizadosSinCsrf() throws Exception {auth();when(service.listar(principal,"TODAS",null)).thenReturn(new NotificacionCuidadorService.Listado(List.of(),null));
  mvc.perform(get("/api/notificaciones").header("Authorization","Bearer "+token).param("idCuidador","999")).andExpect(status().isOk()).andExpect(jsonPath("$.notificaciones").isArray()).andExpect(header().string("Cache-Control","no-store"));
  var n=new NotificacionCuidadorService.Notificacion(1,"AYUDA_GENERAL","Ayuda","Grecia necesita tu ayuda",OffsetDateTime.parse("2026-11-01T07:00:00-05:00"),true,"Grecia",null,1,null);when(service.leer(principal,1)).thenReturn(n);
  mvc.perform(put("/api/notificaciones/1/leida").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true));verify(service).listar(principal,"TODAS",null);verify(service).leer(principal,1);
 }
 @Test void sinSesionNoAccede() throws Exception {mvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void metodosNoAutorizadosPermanecenCerrados() throws Exception {auth();mvc.perform(post("/api/notificaciones").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());verifyNoInteractions(service);}
}
