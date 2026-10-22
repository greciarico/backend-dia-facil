package dia_facil.backend.service.actividades;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ArasaacClientTest {
    // Synthetic fixtures, not proposed default IDs for Desayunar.
    private static final String JSON = "[{\"_id\":123,\"keywords\":[{\"keyword\":\"lavar\"}],\"internal\":true}]";
    @Test void consultaSoloMetadataCodificadaSinBearerNiCookies() {
        var builder = RestClient.builder().baseUrl("https://api.arasaac.org/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.arasaac.org/v1/pictograms/es/search/lavar%20manos%2Fjab%C3%B3n"))
            .andExpect(method(HttpMethod.GET)).andExpect(headerDoesNotExist("Authorization"))
            .andExpect(headerDoesNotExist("Cookie")).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        var results = new ArasaacClient(builder.build(), JsonMapper.builder().build()).buscar("lavar manos/jabón");
        assertEquals(List.of(PictogramaArasaac.de(123, "lavar")), results);
        assertEquals("https://static.arasaac.org/pictograms/123/123_300.png", results.getFirst().urlImagen());
        server.verify();
    }
    @Test void vacioY404SonAusenciaDeResultadosNoFallo() {
        for (boolean notFound : List.of(true, false)) {
            var builder = RestClient.builder().baseUrl("https://api.arasaac.org/v1");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(notFound ? withStatus(HttpStatus.NOT_FOUND) : withSuccess("[]", MediaType.APPLICATION_JSON));
            assertTrue(new ArasaacClient(builder.build(), JsonMapper.builder().build()).buscar("algo").isEmpty()); server.verify();
        }
    }
    @Test void rechazaRespuestasInvalidasSinExponerContenidoYDeduplicaIds() {
        var mapper = JsonMapper.builder().build();
        for (String invalid : List.of("null", "{}", "[{}]", "[{\"_id\":-1,\"keywords\":[]}]", "[{\"_id\":1,\"keywords\":[]}]")) {
            assertEquals("No pudimos consultar los pictogramas. Inténtalo de nuevo.",
                assertThrows(ArasaacClient.NoDisponible.class, () -> ArasaacClient.interpretar(mapper.readTree(invalid))).getMessage());
        }
        assertEquals(1, ArasaacClient.interpretar(mapper.readTree("[" + JSON.substring(1, JSON.length()-1) + "," + JSON.substring(1, JSON.length()-1) + "]")).size());
    }
    @Test void erroresHttpTimeoutYRespuestaExcesivaSonSeguros() {
        for (String kind : List.of("http", "timeout", "large", "invalid")) {
            var builder = RestClient.builder().baseUrl("https://api.arasaac.org/v1");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(request -> {
                if (kind.equals("timeout")) throw new java.net.SocketTimeoutException("private query");
                if (kind.equals("http")) return withServerError().createResponse(request);
                return withSuccess(kind.equals("large") ? "x".repeat(4 * 1024 * 1024 + 1).getBytes(StandardCharsets.UTF_8)
                    : "private invalid content".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON).createResponse(request);
            });
            var error = assertThrows(ArasaacClient.NoDisponible.class,
                () -> new ArasaacClient(builder.build(), JsonMapper.builder().build()).buscar("private query"));
            assertNull(error.getCause()); assertFalse(error.getMessage().contains("private")); server.verify();
        }
    }
    @Test void cacheAcotadaNormalizaExpiraYNoGuardaFallos() {
        var client = mock(ArasaacClient.class); var clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        when(client.buscar(anyString())).thenReturn(List.of(PictogramaArasaac.de(123, "lavar")));
        var service = new PictogramaArasaacService(client, clock);
        assertTrue(service.buscar(null).isEmpty()); assertTrue(service.buscar("ab").isEmpty()); verifyNoInteractions(client);
        assertThrows(IllegalArgumentException.class, () -> service.buscar("x".repeat(121)));
        assertEquals(service.buscar("  Lavar   manos "), service.buscar("lavar manos")); verify(client).buscar("lavar manos");
        when(clock.millis()).thenReturn(300_001L); service.buscar("lavar manos"); verify(client, times(2)).buscar("lavar manos");
        for (int i = 0; i < 101; i++) service.buscar("consulta" + i);
        service.buscar("consulta0"); verify(client, times(2)).buscar("consulta0");
        when(client.buscar("fallo")).thenThrow(new ArasaacClient.NoDisponible());
        assertThrows(ArasaacClient.NoDisponible.class, () -> service.buscar("fallo"));
        assertThrows(ArasaacClient.NoDisponible.class, () -> service.buscar("fallo")); verify(client, times(2)).buscar("fallo");
    }
}
