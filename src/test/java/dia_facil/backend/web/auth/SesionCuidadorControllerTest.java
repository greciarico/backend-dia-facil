package dia_facil.backend.web.auth;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SesionCuidadorController.class, properties = "diafacil.auth.enabled=true")
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class SesionCuidadorControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    private static final String ACCESS = "a_" + "A".repeat(43);
    private static final String REFRESH = "r_" + "B".repeat(43);
    @Test void bearerObtieneIdentidadDeSpringIgnorandoIdEnviadoPorCliente() throws Exception {
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L, new SesionCuidadorService.CuidadorResumen(3, "Grecia", null)));
        var result = mvc.perform(get("/api/auth/cuidadores/sesion").param("idCuidador", "999").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.idCuidador").value(3))
            .andExpect(jsonPath("$.nombre").value("Grecia")).andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
    }
    @Test void noAceptaIdRefreshComoAccessNiTokenPorUrl() throws Exception {
        mvc.perform(get("/api/auth/cuidadores/sesion").param("accessToken", ACCESS)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/cuidadores/sesion").header("Authorization", "Bearer 3")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/cuidadores/sesion").header("Authorization", "Bearer " + REFRESH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(sesiones);
    }
    @Test void distingueAccessVencidoDeSesionRevocada() throws Exception {
        doThrow(new SesionInvalidada(true)).when(sesiones).autenticar(ACCESS);
        mvc.perform(get("/api/auth/cuidadores/sesion").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_REQUIERE_RENOVACION"));
        doThrow(new SesionInvalidada(false)).when(sesiones).autenticar(ACCESS);
        mvc.perform(get("/api/auth/cuidadores/sesion").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
    }
    @Test void renovacionYCierreSonPublicosExactosSinCsrfNiCookie() throws Exception {
        when(sesiones.renovar(REFRESH)).thenReturn(AutenticacionCuidadorControllerTest.concesion());
        mvc.perform(post("/api/auth/cuidadores/renovar-sesion").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + REFRESH + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sesion.tokenType").value("Bearer"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(post("/api/auth/cuidadores/cerrar-sesion").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + REFRESH + "\"}"))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(sesiones).cerrar(REFRESH);
        mvc.perform(post("/api/auth/cuidadores/cerrar-sesion/extra")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/cuidadores/renovar-sesion")).andExpect(status().isForbidden());
    }
    @Test void refreshInvalidoDevuelve401SinInventarSesion() throws Exception {
        mvc.perform(post("/api/auth/cuidadores/renovar-sesion").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + REFRESH + "\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
    }
}
