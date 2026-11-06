package dia_facil.backend.service.actividades;

import java.time.LocalTime;
import java.util.List;

/** Only definition data. Array position defines the persisted order. */
public record CrearActividad(String nombre, String hora, String codigoIcono, List<Paso> pasos) {
    public record Paso(String descripcion, Integer idArasaac) {}

    public CrearActividad validado() {
        String titulo = nombre == null ? "" : nombre.strip();
        if (titulo.isBlank() || titulo.length() > 80)
            throw RechazoActividad.datos("nombre", "Escribe un nombre de hasta 80 caracteres.");
        if (hora == null || !hora.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            throw RechazoActividad.datos("hora", "Selecciona una hora válida.");
        if (codigoIcono == null || !codigoIcono.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") || codigoIcono.length() > 60)
            throw RechazoActividad.datos("codigoIcono", "Selecciona un ícono del catálogo.");
        if (pasos == null || pasos.isEmpty()) throw RechazoActividad.datos("pasos", "Agrega al menos un paso.");
        // No maximum step count policy. The existing SQL type is not a product limit.
        for (int i = 0; i < pasos.size(); i++) {
            var paso = pasos.get(i);
            if (paso == null || paso.descripcion() == null || paso.descripcion().isBlank() || paso.descripcion().length() > 120)
                throw RechazoActividad.datos("pasos[" + i + "].descripcion", "Escribe un paso de hasta 120 caracteres.");
            if (paso.idArasaac() != null && paso.idArasaac() <= 0) throw RechazoActividad.pictograma();
        }
        return new CrearActividad(titulo, hora, codigoIcono, List.copyOf(pasos));
    }
    public LocalTime horaLocal() { return LocalTime.parse(hora); }
}
