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

    @Test void prefijosDeMenosDeTresCaracteresNoConfundenParticulasConActividades() {
        when(repository.consultarCatalogo()).thenReturn(List.of(new Fila("dish-of-food", "Comida", "almorzar"),
            new Fila("dish-of-food", "Comida", "almuerzo"), new Fila("dish-of-food", "Comida", "almorzando"),
            new Fila("bed", "Cama", "descansar"), new Fila("laptop", "Laptop", "laboratorio")));
        for (String consulta : List.of("Ir al colegio", "voy al", "antes de", "en la", "quiero un al", "una de"))
            assertTrue(service.consultar(consulta).isEmpty(), consulta);
        assertEquals("dish-of-food", service.consultar("alm").getFirst().codigo());
        when(repository.consultarCatalogo()).thenReturn(List.of(new Fila("step", "Paso", "ir")));
        assertEquals("step", service.consultar("ir al colegio").getFirst().codigo()); // Whole-word matches remain legitimate.
    }

    @Test void catalogoRealOfreceLosSeisTriosPorTodasLasVariantesSinComidaEnElContextoEscolar() {
        var datos = dia_facil.backend.service.datosbase.LectorDatosBase.leer(tools.jackson.databind.json.JsonMapper.builder().build(),
            new org.springframework.core.io.ClassPathResource("db/datos-base.json"));
        when(repository.consultarCatalogo()).thenReturn(datos.iconos().stream()
            .flatMap(i -> i.palabras().stream().map(p -> (IconoActividadRepository.Fila) new Fila(i.codigo(), i.nombre(), p))).toList());
        var esperados = java.util.Map.of("Desayunar", List.of("bread", "cereal", "coffee"),
            "Prepararse para dormir", List.of("bed", "night", "sleep-mask"), "Estudiar", List.of("laptop", "notes", "open-book"),
            "Ir al colegio", List.of("backpack", "open-book", "school"), "Cenar", List.of("bread", "coffee", "juice"),
            "Almorzar", List.of("dish-of-food", "food", "soup"));
        for (var plantilla : datos.plantillas()) for (String palabra : plantilla.palabras()) {
            assertEquals(esperados.get(plantilla.nombre()), service.consultar("  " + palabra.toUpperCase(java.util.Locale.ROOT) + "  ")
                .stream().map(IconoActividadService.Icono::codigo).toList(), palabra);
        }
        assertEquals(esperados.get("Ir al colegio"), service.consultar("ir   a la ESCUÉLA").stream().map(IconoActividadService.Icono::codigo).toList());
        assertEquals(List.of("bed", "bread", "cereal"), service.consultar("des").stream().map(IconoActividadService.Icono::codigo).toList()); // descansar is a legitimate prefix too.
        assertEquals(esperados.get("Desayunar"), service.consultar("desay").stream().map(IconoActividadService.Icono::codigo).toList());
        assertTrue(datos.iconos().stream().filter(i -> i.codigo().equals("food")).findFirst().orElseThrow()
            .palabras().stream().noneMatch(java.util.Set.of("cena", "cenar", "cenando")::contains));
        assertEquals("food", datos.plantillas().stream().filter(p -> p.nombre().equals("Cenar")).findFirst().orElseThrow().icono());
    }

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
