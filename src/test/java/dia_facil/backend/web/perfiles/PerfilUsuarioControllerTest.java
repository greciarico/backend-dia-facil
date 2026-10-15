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
    @MockitoBean CrearPerfilService creacion;
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
    @Test void postDerivaPropietarioDelPrincipalYDevuelve201SinLocationFicticio() throws Exception {
        autenticar(3); when(creacion.crear(3, "Persona", "person-01")).thenReturn(new PerfilResumen(12, "Persona", "person-01"));
        mvc.perform(post("/api/perfiles").header("Authorization", "Bearer " + ACCESS).contentType("application/json")
                .content("{\"nombre\":\"Persona\",\"avatar\":\"person-01\",\"idCuidador\":999,\"estado\":\"INACTIVO\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.idPerfilUsuario").value(12))
            .andExpect(jsonPath("$.avatar").value("person-01")).andExpect(jsonPath("$.codigoVinculacion").doesNotExist())
            .andExpect(header().doesNotExist("Location")).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verify(creacion).crear(3, "Persona", "person-01");
    }
    @Test void postRechazaLimiteYValidacionConCodigosControlados() throws Exception {
        autenticar(3); when(creacion.crear(3, "Persona", null)).thenThrow(RechazoPerfil.limite());
        mvc.perform(post("/api/perfiles").header("Authorization", "Bearer " + ACCESS).contentType("application/json").content("{\"nombre\":\"Persona\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("LIMITE_PERFILES_ALCANZADO"));
        when(creacion.crear(3, "", null)).thenThrow(new RechazoPerfil("SOLICITUD_INVALIDA", "Ingresa el nombre de la persona.", "nombre"));
        mvc.perform(post("/api/perfiles").header("Authorization", "Bearer " + ACCESS).contentType("application/json").content("{\"nombre\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        mvc.perform(post("/api/perfiles").contentType("application/json").content("{\"nombre\":\"Persona\"}"))
            .andExpect(status().isUnauthorized());
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
        mvc.perform(get("/api/perfiles/12/extra").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isForbidden());
        mvc.perform(post("/api/perfiles/extra").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isForbidden());
    }
    @Test void detalleUsaIdYPrincipalDevuelveSoloResumenSinRelacionNiCredenciales() throws Exception {
        autenticar(3); when(perfiles.detalle(3, 12)).thenReturn(new PerfilResumen(12, "Persona", "person-01"));
        mvc.perform(get("/api/perfiles/12").param("idCuidador", "999").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.idPerfilUsuario").value(12))
            .andExpect(jsonPath("$.nombre").value("Persona")).andExpect(jsonPath("$.avatar").value("person-01"))
            .andExpect(jsonPath("$.codigoVinculacion").doesNotExist()).andExpect(jsonPath("$.idCuidador").doesNotExist())
            .andExpect(jsonPath("$.estado").doesNotExist()).andExpect(jsonPath("$.configuracion").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie"));
        verify(perfiles).detalle(3, 12); verify(perfiles, never()).detalle(999, 12);
    }
    @Test void detalleAjenoInactivoOInexistenteTieneElMismo404() throws Exception {
        autenticar(20);
        for (int id : new int[] {12, 13, 999}) {
            when(perfiles.detalle(20, id)).thenThrow(RechazoPerfil.noEncontrado());
            mvc.perform(get("/api/perfiles/" + id).header("Authorization", "Bearer " + ACCESS))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("No pudimos encontrar este perfil."))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        }
    }
    @Test void detalleSinBearerOConSesionInvalidaNoConsultaServicio() throws Exception {
        mvc.perform(get("/api/perfiles/12")).andExpect(status().isUnauthorized());
        doThrow(new SesionInvalidada(true)).when(sesiones).autenticar(ACCESS);
        mvc.perform(get("/api/perfiles/12").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("SESION_REQUIERE_RENOVACION"));
        doThrow(new SesionInvalidada(false)).when(sesiones).autenticar(ACCESS);
        mvc.perform(get("/api/perfiles/12").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
        verifyNoInteractions(perfiles);
    }
}
