package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.actividades.*;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.perfiles.RechazoPerfil;
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

@WebMvcTest(controllers = ActividadController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class ActividadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean ActividadService actividades;
    @MockitoBean dia_facil.backend.service.actividades.CrearActividadService creacion;
    private static final String ACCESS = "a_" + "A".repeat(43);
    private void autenticar() {
        when(sesiones.autenticar(ACCESS)).thenReturn(new SesionCuidadorService.Identidad(1L,
            new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null)));
    }

    @Test void vacio200SinSesionHttpYPropietarioSoloDesdePrincipal() throws Exception {
        autenticar(); when(actividades.consultar(3, 12)).thenReturn(List.of());
        var result = mvc.perform(get("/api/perfiles/12/actividades").param("idCuidador", "999").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actividades").isArray()).andExpect(jsonPath("$.actividades").isEmpty())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(actividades).consultar(3, 12); verifyNoMoreInteractions(actividades);
    }

    @Test void datosSoloExponenResumenYNoRelacionesInternas() throws Exception {
        autenticar(); when(actividades.consultar(3, 12)).thenReturn(List.of(new ActividadResumen(7, "Desayunar")));
        mvc.perform(get("/api/perfiles/12/actividades").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isOk()).andExpect(content().json("{\"actividades\":[{\"idActividad\":7,\"nombre\":\"Desayunar\"}]}"))
            .andExpect(jsonPath("$.actividades[0].perfilUsuario").doesNotExist())
            .andExpect(jsonPath("$.actividades[0].pictograma").doesNotExist())
            .andExpect(jsonPath("$.actividades[0].estado").doesNotExist());
    }

    @Test void perfilesNoDisponiblesComparten404() throws Exception {
        autenticar();
        for (int id : new int[] {12, 13, 999, 0}) {
            when(actividades.consultar(3, id)).thenThrow(RechazoPerfil.noEncontrado());
            mvc.perform(get("/api/perfiles/" + id + "/actividades").header("Authorization", "Bearer " + ACCESS))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("No pudimos encontrar este perfil."));
        }
    }

    @Test void bearerInvalidoNuncaConsultaYNoHabilitaComandos() throws Exception {
        mvc.perform(get("/api/perfiles/12/actividades")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/perfiles/12/actividades").param("accessToken", ACCESS)).andExpect(status().isUnauthorized());
        for (boolean renovable : new boolean[] {true, false}) {
            doThrow(new SesionInvalidada(renovable)).when(sesiones).autenticar(ACCESS);
            mvc.perform(get("/api/perfiles/12/actividades").header("Authorization", "Bearer " + ACCESS))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value(renovable ? "SESION_REQUIERE_RENOVACION" : "SESION_INVALIDA"));
        }
        verifyNoInteractions(actividades);
        mvc.perform(post("/api/perfiles/12/actividades").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/perfiles/12/actividades/extra").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isForbidden());
    }

    @Test void formatoInvalidoYFalloDeBdMantienenErroresSeguros() throws Exception {
        autenticar();
        mvc.perform(get("/api/perfiles/no-numero/actividades").header("Authorization", "Bearer " + ACCESS)).andExpect(status().isBadRequest());
        when(actividades.consultar(3, 12)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private detail"));
        mvc.perform(get("/api/perfiles/12/actividades").header("Authorization", "Bearer " + ACCESS))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test void crear201UsaPropietarioDelPrincipalSinCsrfNiEntidadJpa() throws Exception {
        autenticar();
        when(creacion.crear(eq(3),eq(12),any())).thenReturn(new ActividadResumen(27,"Desayuno","07:00","bread"));
        mvc.perform(post("/api/perfiles/12/actividades").header("Authorization","Bearer " + ACCESS)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("""
                {"nombre":"Desayuno","hora":"07:00","codigoIcono":"bread","idCuidador":999,
                 "pasos":[{"descripcion":"Paso final","idArasaac":null}]}
                """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.idActividad").value(27))
            .andExpect(jsonPath("$.hora").value("07:00")).andExpect(jsonPath("$.codigoIcono").value("bread"))
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.perfilUsuario").doesNotExist()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(creacion).crear(eq(3),eq(12),any());
    }
    @Test void erroresDelComandoMantienenCodigosYCamposSeguros() throws Exception {
        autenticar();
        for (var codigo : List.of("SOLICITUD_INVALIDA","ICONO_NO_ENCONTRADO","PICTOGRAMA_INVALIDO","CONFLICTO_CREACION")) {
            when(creacion.crear(eq(3),eq(12),any())).thenThrow(new dia_facil.backend.service.actividades.RechazoActividad(codigo,"Mensaje seguro","pasos"));
            mvc.perform(post("/api/perfiles/12/actividades").header("Authorization","Bearer " + ACCESS)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(codigo.equals("CONFLICTO_CREACION")?409:400))
                .andExpect(jsonPath("$.codigo").value(codigo)).andExpect(jsonPath("$.errores[0].campo").value("pasos"));
        }
        when(creacion.crear(eq(3),eq(12),any())).thenThrow(RechazoPerfil.noEncontrado());
        mvc.perform(post("/api/perfiles/12/actividades").header("Authorization","Bearer " + ACCESS)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"));
    }
}
