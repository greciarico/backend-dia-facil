package dia_facil.backend.web.perfiles;

import dia_facil.backend.configuration.*;
import dia_facil.backend.service.auth.*;
import dia_facil.backend.service.perfiles.*;
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

@WebMvcTest(controllers = AccesibilidadController.class)
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class AccesibilidadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesionCuidadorService sesiones;
    @MockitoBean AccesibilidadService ajustes;
    static final String TOKEN = "a_" + "A".repeat(43);
    void autenticar() { when(sesiones.autenticar(TOKEN)).thenReturn(new SesionCuidadorService.Identidad(1L, new SesionCuidadorService.CuidadorResumen(3, "Cuidador", null))); }
    @Test void getDerivaCuidadorDelBearerYSoloExponeTresCampos() throws Exception {
        autenticar(); when(ajustes.consultar(3, 12)).thenReturn(new AccesibilidadService.Preferencias(TamanoTexto.MEDIANO, false, true));
        mvc.perform(get("/api/perfiles/12/accesibilidad").param("idCuidador", "999").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.tamanoTexto").value("MEDIANO")).andExpect(jsonPath("$.lecturaVoz").value(true))
            .andExpect(jsonPath("$.recordatorioMinutos").doesNotExist()).andExpect(jsonPath("$.perfilUsuario").doesNotExist());
        verify(ajustes).consultar(3, 12);
    }
    @Test void patchYRestablecerExpuestosSinCsrfPeroConBearer() throws Exception {
        autenticar(); var result = new AccesibilidadService.Preferencias(TamanoTexto.GRANDE, false, true);
        when(ajustes.actualizar(3, 12, Map.of("tamanoTexto", "GRANDE"))).thenReturn(result); when(ajustes.restablecer(3, 12)).thenReturn(result);
        mvc.perform(patch("/api/perfiles/12/accesibilidad").header("Authorization", "Bearer " + TOKEN).contentType("application/json").content("{\"tamanoTexto\":\"GRANDE\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/perfiles/12/accesibilidad/restablecer").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk());
        verify(ajustes).actualizar(3, 12, Map.of("tamanoTexto", "GRANDE")); verify(ajustes).restablecer(3, 12);
    }
    @Test void erroresSonFuncionalesYSinBearerNoHayAcceso() throws Exception {
        mvc.perform(get("/api/perfiles/12/accesibilidad")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/perfiles/12/accesibilidad").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/perfiles/12/accesibilidad/restablecer")).andExpect(status().isUnauthorized());
        autenticar(); when(ajustes.consultar(3, 12)).thenThrow(RechazoPerfil.noEncontrado());
        mvc.perform(get("/api/perfiles/12/accesibilidad").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"));
        when(ajustes.actualizar(eq(3), eq(12), any())).thenThrow(new RechazoPerfil("SOLICITUD_INVALIDA", "Revisa los ajustes.", "lecturaVoz"));
        mvc.perform(patch("/api/perfiles/12/accesibilidad").header("Authorization", "Bearer " + TOKEN).contentType("application/json").content("{\"lecturaVoz\":null}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
