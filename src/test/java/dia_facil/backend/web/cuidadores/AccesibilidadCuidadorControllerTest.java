package dia_facil.backend.web.cuidadores;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.cuidadores.AccesibilidadCuidadorService;
import dia_facil.backend.persistence.enums.TamanoTexto;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccesibilidadCuidadorController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class AccesibilidadCuidadorControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean AccesibilidadCuidadorService ajustes;
    static final String TOKEN = "a_" + "A".repeat(43), PATH = "/api/cuidador/accesibilidad";
    void auth() { when(sesiones.autenticar(TOKEN)).thenReturn(new SesionCuidadorService.Identidad(1L, new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null))); }
    @Test void usaPrincipalIgnoraQueryYNoExponeDatosDeUsuario() throws Exception {
        auth(); when(ajustes.consultar(3)).thenReturn(new AccesibilidadCuidadorService.Preferencias(TamanoTexto.MEDIANO, false));
        mvc.perform(get(PATH).param("idCuidador", "999").param("idPerfilUsuario", "12").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.tamanoTexto").value("MEDIANO")).andExpect(jsonPath("$.altoContraste").value(false))
            .andExpect(jsonPath("$.lecturaVoz").doesNotExist()).andExpect(jsonPath("$.idCuidador").doesNotExist());
        verify(ajustes).consultar(3); verifyNoMoreInteractions(ajustes);
    }
    @Test void patchYResetRequierenBearerSinCsrfYRespondenDTOConfirmado() throws Exception {
        auth(); var result = new AccesibilidadCuidadorService.Preferencias(TamanoTexto.GRANDE, true);
        when(ajustes.actualizar(3, Map.of("tamanoTexto", "GRANDE"))).thenReturn(result); when(ajustes.restablecer(3)).thenReturn(result);
        mvc.perform(patch(PATH).header("Authorization", "Bearer " + TOKEN).contentType("application/json").content("{\"tamanoTexto\":\"GRANDE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tamanoTexto").value("GRANDE")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(PATH + "/restablecer").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
        verify(ajustes).actualizar(3, Map.of("tamanoTexto", "GRANDE")); verify(ajustes).restablecer(3);
    }
    @Test void anonimoTokenDeDispositivoSesionRevocadaYMetodosNoPermitidosNoAcceden() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/restablecer")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer du_" + "D".repeat(43))).andExpect(status().isUnauthorized());
        when(sesiones.autenticar(TOKEN)).thenThrow(new SesionInvalidada(false));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + TOKEN)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
        mvc.perform(put(PATH).header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden()); verifyNoInteractions(ajustes);
    }
    @Test void rechazoFuncionalEs400SinDetallesInternos() throws Exception {
        auth(); when(ajustes.actualizar(eq(3), any())).thenThrow(new AccesibilidadCuidadorService.SolicitudInvalida());
        mvc.perform(patch(PATH).header("Authorization", "Bearer " + TOKEN).contentType("application/json").content("{\"lecturaVoz\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
