package dia_facil.backend.web.auth;

import dia_facil.backend.configuration.SecurityConfiguration;
import dia_facil.backend.service.auth.AutenticacionCuidadorService;
import dia_facil.backend.web.error.AutenticacionExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static dia_facil.backend.service.auth.AutenticacionCuidadorService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AutenticacionCuidadorController.class, properties = "diafacil.auth.enabled=true")
@Import({SecurityConfiguration.class, AutenticacionExceptionHandler.class})
class AutenticacionCuidadorControllerTest {
    private static final String BASE = "/api/auth/cuidadores/";
    private static final String EMAIL = "grecia@example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AutenticacionCuidadorService service;

    @Test void solicitaCodigoSinAutenticacionNiCsrfYRecortaCorreo() throws Exception {
        var result = mvc.perform(post(BASE + "solicitar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" grecia@example.com \"}"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.estado").value("CODIGO_SOLICITADO"))
            .andExpect(jsonPath("$.reenvioEnSegundos").value(45))
            .andExpect(jsonPath("$.codigo").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(service).solicitarCodigo(EMAIL);
    }

    @Test void verificaActivoConCerosInicialesSinEntregarSesion() throws Exception {
        when(service.verificarCodigo(EMAIL, "000123")).thenReturn(new ResultadoVerificacion(Estado.AUTENTICADO, 3, null, null));
        var result = mvc.perform(post(BASE + "verificar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\",\"codigo\":\"000123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("AUTENTICADO"))
            .andExpect(jsonPath("$.idCuidador").value(3)).andExpect(jsonPath("$.comprobanteRegistro").doesNotExist())
            .andExpect(jsonPath("$.motivo").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(service).verificarCodigo(EMAIL, "000123");
    }

    @Test void correoNuevoRecibeSoloComprobanteYEstado() throws Exception {
        when(service.verificarCodigo(EMAIL, "000123")).thenReturn(
            new ResultadoVerificacion(Estado.REGISTRO_PENDIENTE, null, "comprobante-de-prueba", null));
        mvc.perform(post(BASE + "verificar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\",\"codigo\":\"000123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REGISTRO_PENDIENTE"))
            .andExpect(jsonPath("$.comprobanteRegistro").value("comprobante-de-prueba"))
            .andExpect(jsonPath("$.idCuidador").doesNotExist()).andExpect(jsonPath("$.comprobante").doesNotExist());
    }

    static List<Motivo> rechazos() {
        return List.of(Motivo.CODIGO_INEXISTENTE, Motivo.CODIGO_INCORRECTO, Motivo.CODIGO_VENCIDO,
            Motivo.CODIGO_USADO, Motivo.LIMITE_INTENTOS, Motivo.CUENTA_INACTIVA);
    }

    @ParameterizedTest @MethodSource("rechazos")
    void convierteCadaRechazoFuncionalAProblemDetail(Motivo motivo) throws Exception {
        when(service.verificarCodigo(EMAIL, "000123")).thenReturn(ResultadoVerificacion.rechazado(motivo));
        int expected = motivo == Motivo.LIMITE_INTENTOS ? 429 : motivo == Motivo.CUENTA_INACTIVA ? 403 : 422;
        mvc.perform(post(BASE + "verificar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\",\"codigo\":\"000123\"}"))
            .andExpect(status().is(expected)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(expected)).andExpect(jsonPath("$.codigo").value(motivo.name()))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.instance").value(BASE + "verificar-codigo"))
            .andExpect(header().doesNotExist("Retry-After"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test void reenvioPrematuroDevuelveEsperaConservadora() throws Exception {
        doThrow(new RechazoAutenticacion(Motivo.REENVIO_PREMATURO)).when(service).solicitarCodigo(EMAIL);
        mvc.perform(post(BASE + "solicitar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.codigo").value("REENVIO_PREMATURO"))
            .andExpect(header().string("Retry-After", "45"));
    }

    @ParameterizedTest @ValueSource(strings = {"solicitar-codigo", "verificar-codigo"})
    void correoAusenteVacioInvalidoOLargoNoLlamaAlService(String ruta) throws Exception {
        for (var email : new String[] {"", " ", "invalido", "a".repeat(151) + "@example.com"}) {
            var body = mapper.writeValueAsString(Map.of("email", email, "codigo", "000123"));
            mvc.perform(post(BASE + ruta).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"))
                .andExpect(jsonPath("$.errores[0].rejectedValue").doesNotExist());
        }
        mvc.perform(post(BASE + ruta).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"000123\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + ruta).contentType(MediaType.APPLICATION_JSON).content("{\"email\":null,\"codigo\":\"000123\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"", "123", "1234567", "abcdef", " 000123 "})
    void otpMalFormadoNoConsumeIntentos(String codigo) throws Exception {
        mvc.perform(post(BASE + "verificar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", EMAIL, "codigo", codigo))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
            .andExpect(jsonPath("$.errores[0].campo").value("codigo"));
        verifyNoInteractions(service);
    }

    @Test void otpAusenteJsonIlegibleYTipoContenidoInvalidoSeSanitizan() throws Exception {
        mvc.perform(post(BASE + "verificar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\"}"))
            .andExpect(status().isBadRequest());
        for (var ruta : List.of("solicitar-codigo", "verificar-codigo")) {
            var response = mvc.perform(post(BASE + ruta).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"secreto-no-devolver\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("secreto-no-devolver"));
            mvc.perform(post(BASE + ruta).contentType(MediaType.TEXT_PLAIN).content("secreto-no-devolver"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(service);
    }

    static List<RuntimeException> erroresTecnicos() {
        return List.of(new MailAuthenticationException("secreto-no-devolver"), new MailSendException("secreto-no-devolver"),
            new org.springframework.dao.DataAccessResourceFailureException("secreto-no-devolver"),
            new org.springframework.dao.QueryTimeoutException("secreto-no-devolver"),
            new MailPreparationException("secreto-no-devolver"), new IllegalStateException("secreto-no-devolver"));
    }

    @ParameterizedTest @MethodSource("erroresTecnicos")
    void erroresTecnicosNoExponenMensajesOCausas(RuntimeException error) throws Exception {
        doThrow(error).when(service).solicitarCodigo(EMAIL);
        int expected = error instanceof MailPreparationException || error instanceof IllegalStateException ? 500 : 503;
        var response = mvc.perform(post(BASE + "solicitar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\"}"))
            .andExpect(status().is(expected)).andExpect(jsonPath("$.codigo").value(
                expected == 500 ? "ERROR_INTERNO" : "SERVICIO_NO_DISPONIBLE"))
            .andExpect(jsonPath("$.exception").doesNotExist()).andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("secreto-no-devolver"));
        assertFalse(response.contains(error.getClass().getName()));
    }

    @Test void validacionAdicionalDeCorreoDelServiceSeDevuelveSinDetallesInternos() throws Exception {
        doThrow(new IllegalArgumentException("secreto-no-devolver")).when(service).solicitarCodigo(EMAIL);
        mvc.perform(post(BASE + "solicitar-codigo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grecia@example.com\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
