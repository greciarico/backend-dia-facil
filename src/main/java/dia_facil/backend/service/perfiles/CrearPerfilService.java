package dia_facil.backend.service.perfiles;

import dia_facil.backend.service.avatares.CatalogoAvatares;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;

@Service
public class CrearPerfilService {
    private final CreacionPerfilTransaccionalService transacciones;
    public CrearPerfilService(CreacionPerfilTransaccionalService transacciones) { this.transacciones = transacciones; }

    public PerfilResumen crear(Integer propietario, String nombre, String avatar) {
        var normalizado = nombre == null ? "" : nombre.strip();
        if (normalizado.isBlank()) throw new RechazoPerfil("SOLICITUD_INVALIDA", "Ingresa el nombre de la persona.", "nombre");
        if (normalizado.length() > 60) throw new RechazoPerfil("SOLICITUD_INVALIDA", "El nombre debe tener como máximo 60 caracteres.", "nombre");
        if (avatar != null && !CatalogoAvatares.ids().contains(avatar)) throw new RechazoPerfil("SOLICITUD_INVALIDA", "Selecciona un avatar del catálogo o utiliza la inicial.", "avatar");
        for (int intento = 0; intento < 3; intento++) {
            try { return transacciones.crear(propietario, normalizado, avatar); }
            catch (RuntimeException ex) {
                if (esColisionCodigo(ex)) continue; // Retry only outside the rolled-back transaction.
                if (esLimiteSql(ex)) throw RechazoPerfil.limite();
                throw ex;
            }
        }
        throw new RechazoPerfil("SERVICIO_NO_DISPONIBLE", "No pudimos crear el perfil. Inténtalo más tarde.", null);
    }
    private static boolean esColisionCodigo(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                var name = violation.getConstraintName().replace("`", "").replace("\"", "");
                if (name.equals("uq_perfil_codigo") || name.endsWith(".uq_perfil_codigo")) return true;
            }
        }
        return false;
    }
    private static boolean esLimiteSql(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "45000".equals(sql.getSQLState())
                && sql.getMessage() != null && sql.getMessage().contains("Un cuidador puede administrar como maximo 4 perfiles")) return true;
        }
        return false;
    }
}
