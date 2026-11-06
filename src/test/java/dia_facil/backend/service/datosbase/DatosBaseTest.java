package dia_facil.backend.service.datosbase;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatosBaseTest {
    static DatosBase fuente() { return LectorDatosBase.leer(JsonMapper.builder().build(), new ClassPathResource("db/datos-base.json")); }
    @Test void arasaacSeIdentificaPorIdSinUrlNiNombreUnicoYConservaEtiquetasExistentes() {
        // Synthetic identities. Do not assign default pictograms to the pilot.
        var p = new DatosBase.Pictograma("Acción", null, "ARASAAC", List.of("acción"), 123);
        var otro = new DatosBase.Pictograma("Acción", null, "ARASAAC", List.of(), 124);
        var datos = new DatosBase(List.of(), List.of(p, otro), List.of(new DatosBase.Plantilla("Prueba", null, List.of(),
            List.of(new DatosBase.Paso(1, "Paso", "arasaac:123")))));
        var memoria = new Memoria(); var sync = new SincronizadorDatosBase(memoria);
        assertTrue(sync.sincronizar(datos).conflictos().isEmpty()); assertEquals(0, sync.sincronizar(datos).creados());
        assertEquals(2, memoria.pictogramas.size()); assertNull(memoria.pictogramaArasaac(123).orElseThrow().urlImagen());
        var revisado = new DatosBase.Pictograma("Nueva etiqueta", null, "ARASAAC", List.of("otra"), 123);
        var resultado = sync.sincronizar(new DatosBase(List.of(), List.of(revisado), List.of()));
        assertTrue(resultado.conflictos().contains("pictograma:arasaac:123:etiqueta"));
        assertEquals("Acción", memoria.pictogramaArasaac(123).orElseThrow().nombre());
        assertEquals(2, memoria.pictogramas.size());
    }
    @Test void arasaacRechazaUrlRedundanteFuenteInvalidaIdentidadDuplicadaYReferenciaPorNombre() {
        var valido = new DatosBase.Pictograma("Acción", null, "ARASAAC", List.of(), 123);
        for (var invalido : List.of(
            new DatosBase.Pictograma("Acción", "https://example.invalid", "ARASAAC", List.of(), 123),
            new DatosBase.Pictograma("Acción", null, "otra", List.of(), 123),
            new DatosBase.Pictograma("Acción", null, "ARASAAC", List.of(), -1))) {
            assertThrows(IllegalArgumentException.class, () -> LectorDatosBase.validar(new DatosBase(List.of(), List.of(invalido), List.of())));
        }
        assertThrows(IllegalArgumentException.class, () -> LectorDatosBase.validar(new DatosBase(List.of(), List.of(valido, valido), List.of())));
        assertThrows(IllegalArgumentException.class, () -> LectorDatosBase.validar(new DatosBase(List.of(), List.of(valido),
            List.of(new DatosBase.Plantilla("Prueba", null, List.of(), List.of(new DatosBase.Paso(1, "Paso", "Acción")))))));
    }
    @Test void recursoEmpaquetadoConservaLasDefinicionesActuales() {
        var datos = fuente(); assertEquals(52, datos.iconos().size()); assertEquals(5, datos.pictogramas().size());
        assertEquals(18, datos.iconos().stream().mapToInt(i -> i.palabras().size()).sum());
        assertEquals(13, datos.pictogramas().stream().mapToInt(p -> p.palabras().size()).sum());
        assertEquals(1, datos.plantillas().size());
        var plantilla = datos.plantillas().getFirst(); assertEquals("Desayunar", plantilla.nombre()); assertEquals("bread", plantilla.icono());
        assertEquals(List.of("desayuno", "desayunar", "desayunando"), plantilla.palabras());
        assertEquals(List.of(1, 2, 3, 4, 5), plantilla.pasos().stream().map(DatosBase.Paso::orden).toList());
        assertEquals(List.of("Lávate las manos", "Prepara la mesa", "Sirve la comida", "Come", "Limpia y recoge"), plantilla.pasos().stream().map(DatosBase.Paso::descripcion).toList());
        var ids = List.of(2443, 5549, 36965, 2349, 8688);
        assertEquals(ids, datos.pictogramas().stream().map(DatosBase.Pictograma::idArasaac).toList());
        assertEquals(List.of("enjabonar las manos", "poner la mesa", "servir", "comer", "secar"),
            datos.pictogramas().stream().map(DatosBase.Pictograma::nombre).toList());
        assertEquals(ids.stream().map(id -> "arasaac:" + id).toList(), plantilla.pasos().stream().map(DatosBase.Paso::pictograma).toList());
        assertTrue(datos.pictogramas().stream().allMatch(p -> p.urlImagen() == null && "ARASAAC".equals(p.fuente())));
    }
    @Test void validacionCompletaAntesDeCualquierAccesoPersistente() {
        var datos = fuente(); var store = mock(DatosBaseStore.class); var sync = new SincronizadorDatosBase(store);
        var plantilla = datos.plantillas().getFirst();
        var invalidos = List.of(
            new DatosBase(null, datos.pictogramas(), datos.plantillas()),
            new DatosBase(List.of(datos.iconos().getFirst(), datos.iconos().getFirst()), datos.pictogramas(), datos.plantillas()),
            new DatosBase(datos.iconos(), datos.pictogramas(), List.of(new DatosBase.Plantilla("Desayunar", "inexistente", List.of(), plantilla.pasos()))),
            new DatosBase(datos.iconos(), datos.pictogramas(), List.of(new DatosBase.Plantilla("Desayunar", "bread", List.of(), List.of(plantilla.pasos().getFirst(), plantilla.pasos().getFirst())))),
            new DatosBase(datos.iconos(), datos.pictogramas(), List.of(new DatosBase.Plantilla("Desayunar", "bread", List.of(), List.of(new DatosBase.Paso(1, "x".repeat(121), "Comer"))))),
            new DatosBase(List.of(new DatosBase.Icono("apple", "Manzana", List.of("Café", "cafe"))), List.of(), List.of()));
        invalidos.forEach(invalido -> assertThrows(IllegalArgumentException.class, () -> sync.sincronizar(invalido)));
        verifyNoInteractions(store);
        assertThrows(IllegalArgumentException.class, () -> LectorDatosBase.leer(JsonMapper.builder().build(), new ByteArrayResource("{}".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> LectorDatosBase.leer(JsonMapper.builder().build(), new ByteArrayResource("{\"desconocido\":true}".getBytes())));
    }
    @Test void vacioSeCompletaYLaRepeticionNoInsertaNiCambiaIdentidades() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente();
        var primero = sync.sincronizar(datos); assertEquals(97, primero.creados()); assertTrue(primero.conflictos().isEmpty());
        var identidades = Map.copyOf(store.iconos);
        var segundo = sync.sincronizar(datos); assertEquals(0, segundo.creados()); assertEquals(97, segundo.existentes());
        assertEquals(identidades, store.iconos); assertTrue(segundo.conflictos().isEmpty());
    }
    @Test void reiniciarAntesDelBackfillNoDuplicaPictogramasNiReasignaReferencias() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente();
        sync.sincronizar(datos);
        var aprobados = Map.copyOf(store.pictogramas); var referencias = Map.copyOf(store.pasos);
        store.pictogramas.clear();
        aprobados.values().forEach(p -> store.pictogramas.put("legacy-" + p.id(), new DatosBaseStore.Pictograma(p.id(), "/legacy.png", null)));
        var antes = Map.copyOf(store.pictogramas);
        var resultado = sync.sincronizar(datos);
        assertEquals(0, resultado.creados()); assertEquals(antes, store.pictogramas); assertEquals(referencias, store.pasos);
        assertTrue(resultado.conflictos().contains("pictograma:arasaac:2443:transicion-pendiente"));
        assertTrue(resultado.conflictos().contains("plantilla:Desayunar:dependencias"));
        // Simulate the approved explicit transition, retaining each local identity.
        store.pictogramas.clear(); store.pictogramas.putAll(aprobados);
        var despues = sync.sincronizar(datos); assertEquals(0, despues.creados()); assertTrue(despues.conflictos().isEmpty());
        assertEquals(referencias, store.pasos);
    }
    @Test void completaPasosCompatiblesSinDuplicarLosExistentes() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente(); sync.sincronizar(datos);
        var id = store.plantillas.get("Desayunar").id(); store.pasos.get(id).removeIf(p -> p.orden() == 2);
        assertEquals(1, sync.sincronizar(datos).creados()); assertEquals(5, store.pasos.get(id).size());
        assertEquals(0, sync.sincronizar(datos).creados());
    }
    @Test void conservaEtiquetaYCompletaKeywordsDelCodigoEstable() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente(); sync.sincronizar(datos);
        var pan = store.iconos.get("bread"); store.iconos.put("bread", new DatosBaseStore.Icono(pan.id(), "Etiqueta revisada"));
        store.iconoPalabras.remove(pan.id() + "|cena");
        var resultado = sync.sincronizar(datos); assertEquals(1, resultado.creados());
        assertTrue(resultado.conflictos().contains("icono:bread:etiqueta")); assertEquals("Etiqueta revisada", store.iconos.get("bread").nombre());
    }
    @Test void pictogramaDiferenteNoRecibeKeywordsNiGeneraPlantillaIncompatible() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente(); sync.sincronizar(datos);
        var manos = store.pictogramas.get("arasaac:2443");
        store.pictogramas.put("arasaac:2443", new DatosBaseStore.Pictograma(manos.id(), null, "otra fuente", 2443, manos.nombre()));
        store.picPalabras.remove(manos.id() + "|manos"); var id = store.plantillas.get("Desayunar").id();
        store.pasos.get(id).removeIf(p -> p.orden() == 2);
        var resultado = sync.sincronizar(datos); assertEquals(0, resultado.creados());
        assertFalse(store.picPalabras.contains(manos.id() + "|manos")); assertEquals(4, store.pasos.get(id).size());
        assertTrue(resultado.conflictos().contains("pictograma:enjabonar las manos:atributos"));
        assertTrue(resultado.conflictos().contains("plantilla:Desayunar:dependencias"));
    }
    @Test void pasosDiferentesAdicionalesODuplicadosNoSeCorrigenNiSeMezclan() {
        for (String caso : List.of("descripcion", "pictograma", "extra", "duplicado")) {
            var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente(); sync.sincronizar(datos);
            var id = store.plantillas.get("Desayunar").id(); var actuales = store.pasos.get(id); var original = actuales.getFirst();
            actuales.removeIf(p -> p.orden() == 2); store.plantillaPalabras.remove(id + "|desayuno");
            switch (caso) {
                case "descripcion" -> actuales.set(0, new DatosBaseStore.Paso(1, "Personalizado", original.pictograma()));
                case "pictograma" -> actuales.set(0, new DatosBaseStore.Paso(1, original.descripcion(), null));
                case "extra" -> actuales.add(new DatosBaseStore.Paso(6, "Extra", null));
                case "duplicado" -> actuales.add(original);
            }
            var antes = List.copyOf(actuales); var resultado = sync.sincronizar(datos);
            assertEquals(0, resultado.creados()); assertEquals(antes, store.pasos.get(id));
            assertFalse(store.plantillaPalabras.contains(id + "|desayuno")); assertTrue(resultado.conflictos().contains("plantilla:Desayunar:pasos"));
        }
    }
    @Test void iconoDePlantillaDiferenteInclusoNullSeConserva() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente(); sync.sincronizar(datos);
        var plantilla = store.plantillas.get("Desayunar"); store.plantillas.put("Desayunar", new DatosBaseStore.Plantilla(plantilla.id(), null));
        store.pasos.get(plantilla.id()).clear(); var resultado = sync.sincronizar(datos);
        assertEquals(0, resultado.creados()); assertTrue(store.pasos.get(plantilla.id()).isEmpty());
        assertNull(store.plantillas.get("Desayunar").icono()); assertTrue(resultado.conflictos().contains("plantilla:Desayunar:icono"));
    }
    @Test void ampliacionSoloAnadeYRetirarUnaDefinicionNoBorraFilas() {
        var store = new Memoria(); var sync = new SincronizadorDatosBase(store); var datos = fuente(); sync.sincronizar(datos);
        var iconos = new ArrayList<>(datos.iconos()); iconos.add(new DatosBase.Icono("icono-prueba", "Nuevo", List.of("palabra-prueba")));
        assertEquals(2, sync.sincronizar(new DatosBase(iconos, datos.pictogramas(), datos.plantillas())).creados());
        assertEquals(0, sync.sincronizar(datos).creados()); assertTrue(store.iconos.containsKey("icono-prueba"));
        assertTrue(store.iconoPalabras.contains(store.iconos.get("icono-prueba").id() + "|palabra-prueba"));
    }

    static final class Memoria implements DatosBaseStore {
        int siguiente = 100;
        final Map<String, Icono> iconos = new HashMap<>(); final Map<String, Pictograma> pictogramas = new HashMap<>();
        final Map<String, Plantilla> plantillas = new HashMap<>(); final Map<Integer, List<Paso>> pasos = new HashMap<>();
        final Set<String> iconoPalabras = new HashSet<>(), picPalabras = new HashSet<>(), plantillaPalabras = new HashSet<>();
        public Optional<Icono> icono(String c) { return Optional.ofNullable(iconos.get(c)); }
        public Icono crearIcono(String c, String n) { var i = new Icono(siguiente++, n); iconos.put(c, i); return i; }
        public boolean palabraIcono(Integer i, String p) { return iconoPalabras.contains(i + "|" + p); }
        public void crearPalabraIcono(Integer i, String p) { assertTrue(iconoPalabras.add(i + "|" + p)); }
        public Optional<Pictograma> pictograma(String n) { return Optional.ofNullable(pictogramas.get(n)); }
        public Optional<Pictograma> pictogramaArasaac(Integer id) { return pictogramas.values().stream().filter(p -> id.equals(p.idArasaac())).findFirst(); }
        public Pictograma crearPictogramaArasaac(String n, Integer id) {
            var p = new Pictograma(siguiente++, null, "ARASAAC", id, n); pictogramas.put("arasaac:" + id, p); return p;
        }
        public Pictograma crearPictograma(String n, String u, String f) { var p = new Pictograma(siguiente++, u, f); pictogramas.put(n, p); return p; }
        public boolean palabraPictograma(Integer i, String p) { return picPalabras.contains(i + "|" + p); }
        public void crearPalabraPictograma(Integer i, String p) { assertTrue(picPalabras.add(i + "|" + p)); }
        public Optional<Plantilla> plantilla(String n) { return Optional.ofNullable(plantillas.get(n)); }
        public Plantilla crearPlantilla(String n, Integer i) { var p = new Plantilla(siguiente++, i); plantillas.put(n, p); pasos.put(p.id(), new ArrayList<>()); return p; }
        public List<Paso> pasos(Integer i) { return List.copyOf(pasos.get(i)); }
        public void crearPaso(Integer i, Integer o, String d, Integer p) { pasos.get(i).add(new Paso(o, d, p)); }
        public boolean palabraPlantilla(Integer i, String p) { return plantillaPalabras.contains(i + "|" + p); }
        public void crearPalabraPlantilla(Integer i, String p) { assertTrue(plantillaPalabras.add(i + "|" + p)); }
    }
}
