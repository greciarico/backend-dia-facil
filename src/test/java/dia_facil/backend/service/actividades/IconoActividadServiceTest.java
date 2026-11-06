package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.repository.IconoActividadRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IconoActividadServiceTest {
    private final IconoActividadRepository repository = mock(IconoActividadRepository.class);
    private final IconoActividadService service = new IconoActividadService(repository);
    private record Fila(String getCodigo, String getNombre, String getPalabra) implements IconoActividadRepository.Fila {}

    @Test void catalogoIncluyeIconosSinKeywordsOrdenadosSinDuplicados() {
        when(repository.consultarCatalogo()).thenReturn(List.of(new Fila("bread", "Pan", "desayuno"),
            new Fila("apple", "Manzana", null), new Fila("bread", "Pan", "cenar")));
        var expected = List.of(new IconoActividadService.Icono("apple", "Manzana"), new IconoActividadService.Icono("bread", "Pan"));
        assertEquals(expected, service.consultar(null)); assertEquals(expected, service.consultar("   "));
    }

    @Test void exactaPrimeroLuegoPalabraLuegoPrefijoConMaximoTresSinRelleno() {
        when(repository.consultarCatalogo()).thenReturn(List.of(new Fila("z", "Exacto", "ir a leer"),
            new Fila("y", "Palabra", "leer"), new Fila("b", "Prefijo", "leer cuentos"),
            new Fila("a", "Otro prefijo", "leerse"), new Fila("unrelated", "Ajeno", null),
            new Fila("y", "Palabra", "lectura")));
        assertEquals(List.of("z", "y", "a"), service.consultar("ir a leer").stream().map(IconoActividadService.Icono::codigo).toList());
        assertEquals(List.of("y", "a", "b"), service.consultar("leer").stream().map(IconoActividadService.Icono::codigo).toList());
        assertTrue(service.consultar("dormir").isEmpty());
    }

    @Test void normalizaAcentosMayusculasYEspaciosSinAlterarEtiquetas() {
        when(repository.consultarCatalogo()).thenReturn(List.of(new Fila("bread", "Pan", "desayunar")));
        assertEquals(List.of(new IconoActividadService.Icono("bread", "Pan")), service.consultar("  DESÁYUNAR  "));
        assertEquals(1, service.consultar("Quiero   desayunar").size());
        assertEquals(1, service.consultar("des").size());
    }

    @Test void consultasCortasNoLeenBdYLongitudInvalidaSeRechaza() {
        for (var query : List.of("d", "de", " dé ")) assertTrue(service.consultar(query).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.consultar("a".repeat(81)));
        verifyNoInteractions(repository);
    }
}
