package dia_facil.backend.service.perfiles;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;

@Service
public class VinculacionService {
    private final VinculacionTransaccionalService transacciones;
    public VinculacionService(VinculacionTransaccionalService transacciones) { this.transacciones = transacciones; }
    public VinculacionResumen consultar(Integer propietario, Integer id) { return transacciones.consultar(propietario, id); }
    public VinculacionResumen renovar(Integer propietario, Integer id, String codigoActual) {
        if (codigoActual == null || codigoActual.isBlank() || codigoActual.length() > 20)
            throw new RechazoPerfil("SOLICITUD_INVALIDA", "Consulta el código actual antes de renovarlo.", "codigoActual");
        for (int intento = 0; intento < 3; intento++) {
            try { return transacciones.renovar(propietario, id, codigoActual); }
            catch (RuntimeException error) { if (!colision(error)) throw error; }
        }
        throw new RechazoPerfil("SERVICIO_NO_DISPONIBLE", "No pudimos renovar el código. Inténtalo más tarde.", null);
    }
    private static boolean colision(Throwable error) {
        for (var causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException c && c.getConstraintName() != null) {
                var nombre = c.getConstraintName().replace("`", "").replace("\"", "");
                if (nombre.equals("uq_perfil_codigo") || nombre.endsWith(".uq_perfil_codigo")) return true;
            }
        }
        return false;
    }
}
