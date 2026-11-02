package dia_facil.backend.web.push;
import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.auth.SesionCuidadorService;
import dia_facil.backend.service.push.RegistroPushService;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=RegistroPushController.class)
@Import({SecurityConfiguration.class,AutenticacionExceptionHandler.class})
class RegistroPushHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean RegistroPushService service;
    final UUID id=UUID.randomUUID();
    final String token="a_"+"A".repeat(43),tag="\""+"a".repeat(64)+"\"";
    final SesionCuidadorService.Identidad principal=new SesionCuidadorService.Identidad(
        1L,new SesionCuidadorService.CuidadorResumen(4,"Test",null));
    void auth(){when(sesiones.autenticar(token)).thenReturn(principal);}
    RegistroPushService.Peticion body(){return new RegistroPushService.Peticion(id,"ExpoPushToken[fixture]","ANDROID");}
    String json(){return "{\"idInstalacion\":\""+id+"\",\"expoToken\":\"ExpoPushToken[fixture]\",\"plataforma\":\"ANDROID\"}";}
    @Test void getPutDeleteConservanContratoYExponenRevision() throws Exception {
        auth(); var state=new RegistroPushService.Estado(true,2,1L,4,"project");
        var value=new RegistroPushService.Versionado(state,tag);
        when(service.consultar(principal,id)).thenReturn(value);
        when(service.registrar(principal,body(),tag)).thenReturn(value);
        when(service.eliminar(principal,id,tag)).thenReturn(tag);
        mvc.perform(get("/api/push/registro").param("idInstalacion",id.toString()).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(header().string("ETag",tag))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.activo").value(true)).andExpect(jsonPath("$.expoToken").doesNotExist());
        mvc.perform(put("/api/push/registro").header("Authorization","Bearer "+token).header("If-Match",tag)
            .contentType(MediaType.APPLICATION_JSON).content(json()))
            .andExpect(status().isOk()).andExpect(header().string("ETag",tag));
        mvc.perform(delete("/api/push/registro").param("idInstalacion",id.toString())
            .header("Authorization","Bearer "+token).header("If-Match",tag))
            .andExpect(status().isNoContent()).andExpect(header().string("ETag",tag))
            .andExpect(header().string("Cache-Control","no-store"));
        verify(service).registrar(principal,body(),tag);verify(service).eliminar(principal,id,tag);
    }
    @Test void excepcionesDePrecondicionConservan412Y428ConAdviceReal() throws Exception {
        auth();
        when(service.registrar(principal,body(),tag)).thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED));
        when(service.eliminar(principal,id,null)).thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED));
        mvc.perform(put("/api/push/registro").header("Authorization","Bearer "+token).header("If-Match",tag)
            .contentType(MediaType.APPLICATION_JSON).content(json())).andExpect(status().isPreconditionFailed());
        mvc.perform(delete("/api/push/registro").param("idInstalacion",id.toString())
            .header("Authorization","Bearer "+token)).andExpect(status().isPreconditionRequired());
    }
    @Test void sinSesionOMetodoAjenoNoSeAccede() throws Exception {
        mvc.perform(get("/api/push/registro").param("idInstalacion",id.toString())).andExpect(status().isUnauthorized());
        auth();mvc.perform(post("/api/push/registro").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
