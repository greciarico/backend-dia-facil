package dia_facil.backend.web.actividades;

import dia_facil.backend.configuration.*;
import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.service.perfiles.AccesibilidadService;
import dia_facil.backend.persistence.entity.PerfilUsuario;
import dia_facil.backend.persistence.enums.TamanoTexto;
import dia_facil.backend.web.dispositivos.AccesibilidadUsuarioController;
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

@WebMvcTest(controllers = AccesibilidadUsuarioController.class, properties = "diafacil.dispositivos.enabled=true")
@Import({SecurityConfiguration.class, DispositivoSecurityConfiguration.class, AutenticacionExceptionHandler.class})
class AccesibilidadUsuarioControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccesoDispositivoService acceso;
    @MockitoBean AccesibilidadService ajustes;
    static final String TOKEN = "du_" + "D".repeat(43);
    @Test void resuelvePerfilExclusivamenteConE3() throws Exception {
        var identidad = new AccesoDispositivoService.Identidad(1L, 12); var perfil = new dia_facil.backend.service.perfiles.PerfilResumen(12, "Persona", null);
        when(acceso.autenticar(TOKEN)).thenReturn(identidad); when(acceso.perfilActivo(identidad)).thenReturn(perfil);
        when(ajustes.consultarUsuario(12)).thenReturn(new AccesibilidadService.Preferencias(TamanoTexto.GRANDE, true, false));
        mvc.perform(get("/api/usuario/accesibilidad").param("idPerfilUsuario", "999").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.tamanoTexto").value("GRANDE")).andExpect(jsonPath("$.altoContraste").value(true));
        verify(acceso).perfilActivo(identidad); verify(ajustes).consultarUsuario(12); verifyNoMoreInteractions(ajustes);
    }
    @Test void requiereUsuarioYRevocacionImpideAcceder() throws Exception {
        mvc.perform(get("/api/usuario/accesibilidad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuario/accesibilidad").header("Authorization", "Bearer a_" + "A".repeat(43))).andExpect(status().isUnauthorized());
        when(acceso.autenticar(TOKEN)).thenReturn(new AccesoDispositivoService.Identidad(1L, null));
        mvc.perform(get("/api/usuario/accesibilidad").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden());
        when(acceso.autenticar(TOKEN)).thenThrow(RechazoDispositivo.invalida());
        mvc.perform(get("/api/usuario/accesibilidad").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(ajustes);
    }
}
