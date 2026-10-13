package dia_facil.backend.web.perfiles;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.perfiles.*;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PerfilUsuarioController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class PerfilUsuarioControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean PerfilUsuarioService perfiles;
    private static final String ACCESS = "a_" + "A".repeat(43);
    private void autenticar(int id) {
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
            new SesionCuidadorService.CuidadorResumen(id, "Cuidador", null)));
    }
    @Test void derivaPropietarioDelPrincipalEIgnoraIdEnQuery() throws Exception {
        autenticar(3);
        when(perfiles.consultar(3)).thenReturn(List.of(new PerfilResumen(12, "Persona", null)));
        var result = mvc.perform(get("/api/perfiles").param("idCuidador", "999").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.maximoPerfilesActivos").value(4))
            .andExpect(jsonPath("$.perfiles[0].idPerfilUsuario").value(12))
            .andExpect(jsonPath("$.perfiles[0].nombre").value("Persona"))
            .andExpect(jsonPath("$.perfiles[0].avatar").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.perfiles[0].codigoVinculacion").doesNotExist())
            .andExpect(jsonPath("$.perfiles[0].idCuidador").doesNotExist())
            .andExpect(jsonPath("$.perfiles[0].actividades").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(perfiles).consultar(3); verify(perfiles, never()).consultar(999);
    }
    @Test void cuidadorSinPerfilesRecibe200ConListaVacia() throws Exception {
        autenticar(7); when(perfiles.consultar(7)).thenReturn(List.of());
        mvc.perform(get("/api/perfiles").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.perfiles").isEmpty());
    }
    @Test void noConfiaEnQueryNiIdNiRefreshComoCredencial() throws Exception {
        for (var request : List.of(get("/api/perfiles"), get("/api/perfiles").param("accessToken", ACCESS),
                get("/api/perfiles").header("Authorization", "Bearer 3"),
                get("/api/perfiles").header("Authorization", "Bearer r_" + "B".repeat(43)))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(perfiles, sesiones);
    }
    @Test void sesionVencidaRevocadaOCuidadorInactivoNoConsultaPerfiles() throws Exception {
        for (var renewable : new boolean[] {true, false}) {
            doThrow(new SesionInvalidada(renewable)).when(sesiones).autenticar(ACCESS);
            mvc.perform(get("/api/perfiles").header("Authorization", "Bearer " + ACCESS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(renewable ? "SESION_REQUIERE_RENOVACION" : "SESION_INVALIDA"));
        }
        verifyNoInteractions(perfiles);
    }
    @Test void soloGetExactoEstaHabilitadoYFalloDeBdEsRecuperable() throws Exception {
        autenticar(3);
        when(perfiles.consultar(3)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private detail"));
        mvc.perform(get("/api/perfiles").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
        mvc.perform(get("/api/perfiles/12").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isForbidden());
        mvc.perform(post("/api/perfiles").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isForbidden());
    }
}
