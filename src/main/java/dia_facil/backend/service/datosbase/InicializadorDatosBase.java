package dia_facil.backend.service.datosbase;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class InicializadorDatosBase {
    private final DatosBase datos;
    private final SincronizadorDatosBase sincronizador;
    private final VerificadorEsquemaDatosBase esquema;
    private final TransactionTemplate transaccion;

    public InicializadorDatosBase(DatosBase datos, SincronizadorDatosBase sincronizador,
            VerificadorEsquemaDatosBase esquema, PlatformTransactionManager manager) {
        this.datos = datos; this.sincronizador = sincronizador; this.esquema = esquema;
        transaccion = new TransactionTemplate(manager);
        transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public SincronizadorDatosBase.Resultado inicializar() {
        LectorDatosBase.validar(datos);
        RuntimeException ultimo = null;
        for (int intento = 0; intento < 3; intento++) {
            try {
                return transaccion.execute(status -> { esquema.verificar(); return sincronizador.sincronizar(datos); });
            } catch (RuntimeException ex) {
                if (!reintentable(ex)) throw ex;
                ultimo = ex; // A fresh transaction/EntityManager sees the winner's committed rows.
            }
        }
        throw new IllegalStateException("No se pudieron inicializar datos base tras tres intentos; revise concurrencia/BD.", ultimo);
    }

    static boolean reintentable(Throwable error) {
        for (var causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException constraint && constraint.getConstraintName() != null) {
                var nombre = constraint.getConstraintName().replace("`", "").replace("\"", "").toLowerCase(java.util.Locale.ROOT);
                if (VerificadorEsquemaDatosBase.REQUERIDAS.stream().anyMatch(u -> nombre.equals(u.nombre()) || nombre.endsWith("." + u.nombre()))) return true;
            }
            if (causa instanceof java.sql.SQLException sql && (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205)) return true;
        }
        return false;
    }
}
