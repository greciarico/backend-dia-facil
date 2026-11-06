package dia_facil.backend.service.actividades;

import dia_facil.backend.persistence.repository.PlantillaActividadRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlantillaActividadServiceTest {
    private final PlantillaActividadRepository repository = mock(PlantillaActividadRepository.class);
    private final PlantillaActividadService service = new PlantillaActividadService(repository);
    private record Candidata(Integer getId, String getNombre, String getPalabra) implements PlantillaActividadRepository.Candidata {}
    private record Paso(Integer getOrden, String getDescripcion, Integer getIdPictograma, String getNombrePictograma, Integer getIdArasaac) implements PlantillaActividadRepository.Paso {
        Paso(Integer orden, String descripcion, Integer id, String nombre) { this(orden, descripcion, id, nombre, null); }
    }

    @Test void lasSeisPlantillasSeEncuentranPorTodasSusVariantesYEntreganPasosOpcionales() {
        var datos = dia_facil.backend.service.datosbase.LectorDatosBase.leer(tools.jackson.databind.json.JsonMapper.builder().build(),
            new org.springframework.core.io.ClassPathResource("db/datos-base.json"));
        var candidatas = new java.util.ArrayList<PlantillaActividadRepository.Candidata>();
        for (int index = 0; index < datos.plantillas().size(); index++) {
            var plantilla = datos.plantillas().get(index); int id = index + 100;
            plantilla.palabras().forEach(p -> candidatas.add(new Candidata(id, plantilla.nombre(), p)));
            when(repository.pasos(id)).thenReturn(plantilla.pasos().stream().map(p -> {
                var pic = datos.pictogramas().stream().filter(i -> i.clave().equals(p.pictograma())).findFirst();
                return (PlantillaActividadRepository.Paso) new Paso(p.orden(), p.descripcion(), pic.isEmpty() ? null : pic.get().idArasaac(),
                    pic.isEmpty() ? null : pic.get().nombre(), pic.isEmpty() ? null : pic.get().idArasaac());
            }).toList());
        }
        when(repository.candidatas()).thenReturn(candidatas);
        for (var plantilla : datos.plantillas()) {
            var consultas = new java.util.ArrayList<>(plantilla.palabras()); consultas.add(plantilla.nombre());
            for (String consulta : consultas) {
                var resultado = service.sugerir("  " + consulta.toUpperCase(java.util.Locale.ROOT) + "  ");
                assertNotNull(resultado, consulta); assertEquals(plantilla.nombre(), resultado.nombre(), consulta);
                assertEquals(plantilla.pasos().stream().map(p -> p.descripcion()).toList(),
                    resultado.pasos().stream().map(p -> p.descripcion()).toList());
                assertEquals(java.util.stream.IntStream.rangeClosed(1, plantilla.pasos().size()).boxed().toList(),
                    resultado.pasos().stream().map(p -> p.orden()).toList());
                for (int i = 0; i < plantilla.pasos().size(); i++) {
                    String referencia = plantilla.pasos().get(i).pictograma(); var pictograma = resultado.pasos().get(i).pictograma();
                    if (referencia == null) assertNull(pictograma);
                    else assertEquals(referencia, "arasaac:" + pictograma.idArasaac());
                }
            }
        }
        for (String ambigua : List.of("comer", "comida", "clases", "tarea")) assertNull(service.sugerir(ambigua), ambigua);
        assertEquals("Ir al colegio", service.sugerir("ir   a la ESCUÉLA").nombre());
        assertEquals("Estudiar", service.sugerir("hacer la tarea de hoy").nombre());
    }

    @Test void desayunarEntregaLosCincoPredeterminadosAprobadosConTextosOriginales() {
        var datos = dia_facil.backend.service.datosbase.LectorDatosBase.leer(tools.jackson.databind.json.JsonMapper.builder().build(),
            new org.springframework.core.io.ClassPathResource("db/datos-base.json"));
        var plantilla = datos.plantillas().getFirst();
        when(repository.candidatas()).thenReturn(List.of(new Candidata(90, plantilla.nombre(), "desayuno")));
        when(repository.pasos(90)).thenReturn(java.util.stream.IntStream.range(0,5).mapToObj(index -> {
            var paso = plantilla.pasos().get(index); var pictograma = datos.pictogramas().get(index);
            return (PlantillaActividadRepository.Paso) new Paso(paso.orden(), paso.descripcion(), index+2, pictograma.nombre(), pictograma.idArasaac());
        }).toList());
        var resultado = service.sugerir("Desayuno");
        assertEquals(List.of("Lávate las manos","Prepara la mesa","Sirve la comida","Come","Limpia y recoge"),
            resultado.pasos().stream().map(PlantillaActividadService.Paso::descripcion).toList());
        assertEquals(List.of(2443,5549,36965,2349,8688), resultado.pasos().stream().map(p -> p.pictograma().idArasaac()).toList());
        for (var paso : resultado.pasos()) assertEquals(PictogramaArasaac.imagen(paso.pictograma().idArasaac()), paso.pictograma().urlImagen());
        verify(repository).candidatas(); verify(repository).pasos(90); verifyNoMoreInteractions(repository);
    }

    @Test void construyeImagenDesdeIdentidadExternaSinConsultarArasaac() {
        when(repository.candidatas()).thenReturn(List.of(new Candidata(4, "Desayunar", null)));
        when(repository.pasos(4)).thenReturn(List.of(new Paso(1, "Paso de prueba", 9, "Pictograma de prueba", 123)));
        var pictograma = service.sugerir("Desayunar").pasos().getFirst().pictograma();
        assertEquals(9, pictograma.idPictograma()); assertEquals(123, pictograma.idArasaac());
        assertEquals("https://static.arasaac.org/pictograms/123/123_300.png", pictograma.urlImagen());
    }

    @Test void sinNombreOCortoNoConsultaNiInventaPasos() {
        for (var value : new String[] {null, "", "  ", "de", " d "}) assertNull(service.sugerir(value));
        assertThrows(IllegalArgumentException.class, () -> service.sugerir("a".repeat(81)));
        verifyNoInteractions(repository);
    }

    @Test void usaNombreYKeywordsNormalizadosSinCoincidenciasPorPrefijo() {
        when(repository.candidatas()).thenReturn(List.of(new Candidata(4, "Desayunar", "desayuno"),
            new Candidata(4, "Desayunar", "desayunando")));
        when(repository.pasos(4)).thenReturn(List.of(new Paso(1, "Lávate las manos", 9, "Lavarse las manos"),
            new Paso(2, "Prepara la mesa", null, null)));
        assertNull(service.sugerir("Des")); assertNull(service.sugerir("desayunaremos"));
        assertNull(service.sugerir("Visitar a la abuela"));
        for (var query : List.of(" DESÁYUNAR ", "Desayuno del domingo", "Estoy   desayunando")) {
            var result = service.sugerir(query);
            assertEquals(4, result.idPlantillaActividad());
            assertEquals("Lávate las manos", result.pasos().getFirst().descripcion());
            assertEquals(9, result.pasos().getFirst().pictograma().idPictograma());
            assertNull(result.pasos().get(1).pictograma());
            assertEquals(List.of(1, 2), result.pasos().stream().map(PlantillaActividadService.Paso::orden).toList());
        }
        verify(repository, times(3)).pasos(4);
    }

    @Test void priorizaNombreExactoSobreKeywordYSobreFraseYDesempataDeFormaEstable() {
        when(repository.candidatas()).thenReturn(List.of(new Candidata(3, "Zeta", "leer"),
            new Candidata(2, "Beta", "leer"), new Candidata(1, "Leer", null)));
        when(repository.pasos(anyInt())).thenReturn(List.of());
        assertEquals(1, service.sugerir("leer").idPlantillaActividad());
        assertEquals(2, service.sugerir("quiero leer").idPlantillaActividad());
        when(repository.candidatas()).thenReturn(List.of(new Candidata(8, "Zeta", "leer"), new Candidata(5, "Beta", "estudiar")));
        assertEquals(8, service.sugerir("leer").idPlantillaActividad());
        when(repository.candidatas()).thenReturn(List.of(new Candidata(7, "Igual", "leer"), new Candidata(4, "Igual", "leer")));
        assertEquals(4, service.sugerir("leer").idPlantillaActividad());
    }
}
