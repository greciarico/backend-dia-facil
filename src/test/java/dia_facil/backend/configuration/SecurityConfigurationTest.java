package dia_facil.backend.configuration;

import dia_facil.backend.web.auth.AutenticacionCuidadorController;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AutenticacionCuidadorController.class, properties = "diafacil.auth.enabled=false")
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test void moduloDesactivadoNoRegistraControllerNiExponeServicio() throws Exception {
        assertTrue(context.getBeansOfType(AutenticacionCuidadorController.class).isEmpty());
        for (var ruta : new String[] {"solicitar-codigo", "verificar-codigo", "completar-registro"}) {
            mvc.perform(post("/api/auth/cuidadores/" + ruta).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"/api/auth/cuidadores/solicitar-codigo", "/api/auth/cuidadores/verificar-codigo",
        "/api/auth/cuidadores/completar-registro", "/api/perfiles", "/login", "/logout"})
    void getNoAbreRutasNiRedirigeAlLogin(String path) throws Exception {
        var result = mvc.perform(get(path)).andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
            .andExpect(jsonPath("$.status").value(403)).andExpect(header().doesNotExist("Location"))
            .andExpect(header().doesNotExist("WWW-Authenticate")).andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test @WithMockUser
    void otrasRutasSiguenCerradasInclusoConUsuarioAutenticado() throws Exception {
        mvc.perform(get("/api/perfiles")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void csrfSoloSeExcluyeEnLosTresPostExactosYCorsNoEsPermisivo() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                post("/api/auth/cuidadores/completar-registro/extra"), put("/api/auth/cuidadores/completar-registro"), put("/api/auth/cuidadores/verificar-codigo"),
                post("/api/auth/cuidadores/verificar-codigo/extra")}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        }
        mvc.perform(options("/api/auth/cuidadores/verificar-codigo").header("Origin", "https://no-autorizado.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void credencialesBasicNoHabilitanOtrasRutas() throws Exception {
        mvc.perform(get("/api/perfiles").header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
}
