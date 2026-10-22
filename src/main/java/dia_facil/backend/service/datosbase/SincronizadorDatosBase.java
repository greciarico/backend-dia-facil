package dia_facil.backend.service.datosbase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class SincronizadorDatosBase {
    public record Resultado(int creados, int existentes, List<String> conflictos) {
        public Resultado { conflictos = List.copyOf(conflictos); }
    }
    private final DatosBaseStore store;
    public SincronizadorDatosBase(DatosBaseStore store) { this.store = store; }

    public Resultado sincronizar(DatosBase datos) {
        LectorDatosBase.validar(datos); // Also safe when called directly, before the first store operation.
        var informe = new Informe();
        var pendientes = referenciasPendientes(datos);
        var iconos = new HashMap<String, Integer>(); var pictogramas = new HashMap<String, Integer>();
        for (var base : datos.iconos()) {
            var encontrado = store.icono(base.codigo());
            var icono = encontrado.orElseGet(() -> informe.crear(() -> store.crearIcono(base.codigo(), base.nombre())));
            if (encontrado.isPresent()) {
                informe.existentes++;
                if (!Objects.equals(icono.nombre(), base.nombre())) informe.conflictos.add("icono:" + base.codigo() + ":etiqueta");
            }
            iconos.put(clave(base.codigo()), icono.id());
            for (String palabra : base.palabras()) informe.palabra(() -> store.palabraIcono(icono.id(), palabra), () -> store.crearPalabraIcono(icono.id(), palabra));
        }
        for (var base : datos.pictogramas()) {
            if (pendientes.contains(clave(base.clave()))) {
                informe.conflictos.add("pictograma:" + base.clave() + ":transicion-pendiente"); continue;
            }
            var encontrado = base.idArasaac() == null ? store.pictograma(base.nombre()) : store.pictogramaArasaac(base.idArasaac());
            var pictograma = encontrado.orElseGet(() -> informe.crear(() -> base.idArasaac() == null
                ? store.crearPictograma(base.nombre(), base.urlImagen(), base.fuente()) : store.crearPictogramaArasaac(base.nombre(), base.idArasaac())));
            if (encontrado.isPresent()) informe.existentes++;
            if (!Objects.equals(pictograma.idArasaac(), base.idArasaac()) || !Objects.equals(pictograma.fuente(), base.fuente())
                || (base.idArasaac() == null && !Objects.equals(pictograma.urlImagen(), base.urlImagen()))) {
                informe.conflictos.add("pictograma:" + base.nombre() + ":atributos"); continue;
            }
            if (base.idArasaac() != null && !Objects.equals(pictograma.nombre(), base.nombre())) informe.conflictos.add("pictograma:" + base.clave() + ":etiqueta");
            pictogramas.put(clave(base.clave()), pictograma.id());
            for (String palabra : base.palabras()) informe.palabra(() -> store.palabraPictograma(pictograma.id(), palabra), () -> store.crearPalabraPictograma(pictograma.id(), palabra));
        }
        for (var base : datos.plantillas()) {
            if (base.icono() != null && !iconos.containsKey(clave(base.icono()))
                || base.pasos().stream().anyMatch(p -> p.pictograma() != null && !pictogramas.containsKey(clave(p.pictograma())))) {
                informe.conflictos.add("plantilla:" + base.nombre() + ":dependencias"); continue;
            }
            Integer icono = base.icono() == null ? null : iconos.get(clave(base.icono()));
            var encontrado = store.plantilla(base.nombre());
            var plantilla = encontrado.orElseGet(() -> informe.crear(() -> store.crearPlantilla(base.nombre(), icono)));
            if (encontrado.isPresent()) informe.existentes++;
            if (!Objects.equals(plantilla.icono(), icono)) {
                informe.conflictos.add("plantilla:" + base.nombre() + ":icono"); continue;
            }
            var esperados = new HashMap<Integer, DatosBaseStore.Paso>();
            for (var paso : base.pasos()) esperados.put(paso.orden(), new DatosBaseStore.Paso(paso.orden(), paso.descripcion(),
                paso.pictograma() == null ? null : pictogramas.get(clave(paso.pictograma()))));
            var actuales = store.pasos(plantilla.id()); var posiciones = new HashSet<Integer>();
            boolean compatible = actuales.stream().allMatch(p -> posiciones.add(p.orden()) && p.equals(esperados.get(p.orden())));
            if (!compatible) {
                informe.conflictos.add("plantilla:" + base.nombre() + ":pasos"); continue;
            }
            informe.existentes += actuales.size();
            // Compare the WHOLE group first; never fill holes in an incompatible template.
            base.pasos().stream().sorted(java.util.Comparator.comparing(DatosBase.Paso::orden)).forEach(p -> {
                if (!posiciones.contains(p.orden())) {
                    var esperado = esperados.get(p.orden());
                    store.crearPaso(plantilla.id(), esperado.orden(), esperado.descripcion(), esperado.pictograma()); informe.creados++;
                }
            });
            for (String palabra : base.palabras()) informe.palabra(() -> store.palabraPlantilla(plantilla.id(), palabra), () -> store.crearPalabraPlantilla(plantilla.id(), palabra));
        }
        return new Resultado(informe.creados, informe.existentes, informe.conflictos);
    }
    private static String clave(String valor) { return LectorDatosBase.comparable(valor); }
    private java.util.Set<String> referenciasPendientes(DatosBase datos) {
        // Do not create replacement rows before an explicit backfill of an existing
        // template's references. This is a read-only preflight, not a migration.
        var bases = new HashMap<String, DatosBase.Pictograma>();
        datos.pictogramas().forEach(p -> bases.put(clave(p.clave()), p));
        var pendientes = new HashSet<String>();
        for (var plantilla : datos.plantillas()) {
            var existente = store.plantilla(plantilla.nombre());
            if (existente.isEmpty()) continue;
            var actuales = store.pasos(existente.get().id());
            for (var paso : plantilla.pasos()) {
                if (paso.pictograma() == null || actuales.stream().noneMatch(p -> p.orden().equals(paso.orden()))) continue;
                var base = bases.get(clave(paso.pictograma()));
                if (base != null && base.idArasaac() != null && store.pictogramaArasaac(base.idArasaac()).isEmpty())
                    pendientes.add(clave(base.clave()));
            }
        }
        return pendientes;
    }
    private static final class Informe {
        int creados; int existentes; final List<String> conflictos = new ArrayList<>();
        <T> T crear(Supplier<T> crear) { var valor = crear.get(); creados++; return valor; }
        void palabra(BooleanSupplier existe, Runnable crear) { if (existe.getAsBoolean()) existentes++; else { crear.run(); creados++; } }
    }
}
