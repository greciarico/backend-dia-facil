package dia_facil.backend.web.dispositivos;
import dia_facil.backend.configuration.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.PerfilResumen;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=DispositivoController.class,properties="diafacil.dispositivos.enabled=true")
@Import({SecurityConfiguration.class,DispositivoSecurityConfiguration.class,AutenticacionExceptionHandler.class})
class DispositivoControllerTest {
    @Autowired MockMvc mvc; @MockitoBean AccesoDispositivoService service;
    @Autowired org.springframework.context.ApplicationContext context;
    static final String TOKEN="du_"+"D".repeat(43);
    void auth(Integer perfil){when(service.autenticar(TOKEN)).thenReturn(new AccesoDispositivoService.Identidad(1L,perfil));}
    @Test void preparaSinCodigoNiSesionDelCuidadorYNoCache() throws Exception {
        org.junit.jupiter.api.Assertions.assertNotNull(context.getBean(DispositivoController.class));
        when(service.preparar()).thenReturn(new AccesoDispositivoService.Preparacion(TOKEN));
        mvc.perform(post("/api/dispositivos/preparar-vinculacion")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.credencialDispositivo").value(TOKEN)).andExpect(header().string("Cache-Control","no-store"));
        verify(service).preparar();verify(service,never()).vincular(any(),any(),any());
    }
    @Test void vincularUsaPrincipalYNoAutoridadDelBody() throws Exception {
        auth(null);when(service.vincular(1L,"DF-7KQ9W6H2M8V3X5RA","Test",null)).thenReturn(new AccesoDispositivoService.Vinculacion("VINCULADO",new PerfilResumen(12,"Persona",null),null));
        mvc.perform(post("/api/dispositivos/vincular").header("Authorization","Bearer "+TOKEN).contentType("application/json")
            .content("{\"codigoVinculacion\":\"DF-7KQ9W6H2M8V3X5RA\",\"nombreDispositivo\":\"Test\",\"idPerfilUsuario\":999,\"idCuidador\":999}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.perfil.idPerfilUsuario").value(12));
        verify(service).vincular(1L,"DF-7KQ9W6H2M8V3X5RA","Test",null);
    }
    @Test void mismoErrorPublicoYBearerNoIntercambiable() throws Exception {
        auth(null);when(service.vincular(1L,"DF-482157","Test",null)).thenThrow(RechazoDispositivo.codigoInvalido());
        mvc.perform(post("/api/dispositivos/vincular").header("Authorization","Bearer "+TOKEN).contentType("application/json")
            .content("{\"codigoVinculacion\":\"DF-482157\",\"nombreDispositivo\":\"Test\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("CODIGO_VINCULACION_INVALIDO"));
        mvc.perform(get("/api/dispositivos/vinculacion").header("Authorization","Bearer a_"+"A".repeat(43))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/perfiles").header("Authorization","Bearer "+TOKEN)).andExpect(status().isUnauthorized());
    }
    @Test void restauraYRevocaSoloIdentidadDelBearer() throws Exception {
        auth(12);when(service.consultar(1L)).thenReturn(new AccesoDispositivoService.Estado("VINCULADO",new PerfilResumen(12,"Persona",null)));
        mvc.perform(get("/api/dispositivos/vinculacion").param("idPerfilUsuario","999").header("Authorization","Bearer "+TOKEN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.perfil.idPerfilUsuario").value(12));
        mvc.perform(delete("/api/dispositivos/vinculacion").header("Authorization","Bearer "+TOKEN)).andExpect(status().isForbidden());verify(service,never()).salir(any());
        auth(null);mvc.perform(delete("/api/dispositivos/vinculacion").header("Authorization","Bearer "+TOKEN)).andExpect(status().isNoContent());verify(service).salir(1L);
    }
}
