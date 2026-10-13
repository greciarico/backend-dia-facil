package dia_facil.backend.service.auth;

import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Application facade. Production bean assembly and the delivery adapter are intentionally pending. */
public class AutenticacionCuidadorService {
    private final AutenticacionCuidadorTransaccionalService transacciones;
    private final CriptografiaAutenticacion criptografia;
    private final EnvioCodigoAcceso envio;

    public AutenticacionCuidadorService(AutenticacionCuidadorTransaccionalService transacciones,
            CriptografiaAutenticacion criptografia, EnvioCodigoAcceso envio) {
        this.transacciones = Objects.requireNonNull(transacciones);
        this.criptografia = Objects.requireNonNull(criptografia);
        this.envio = Objects.requireNonNull(envio);
    }

    public void solicitarCodigo(String email) {
        var emision = transacciones.emitirCodigo(CriptografiaAutenticacion.normalizarCorreo(email));
        Runnable enviar = () -> envio.enviar(emision.email(), emision.codigo());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { enviar.run(); }
            });
        } else {
            // The REQUIRED collaborator has already committed when invoked without an outer transaction.
            enviar.run();
        }
    }

    public ResultadoVerificacion verificarCodigo(String email, String codigo) {
        return transacciones.verificarCodigo(CriptografiaAutenticacion.normalizarCorreo(email), codigo);
    }

    public Integer completarRegistro(String comprobante, String nombre) {
        return completarRegistro(comprobante, nombre, null);
    }

    public Integer completarRegistro(String comprobante, String nombre, String avatarId) {
        final CriptografiaAutenticacion.Comprobante acreditacion;
        try {
            acreditacion = criptografia.validarComprobante(comprobante);
        } catch (IllegalArgumentException ex) {
            throw new RechazoAutenticacion(Motivo.COMPROBANTE_INVALIDO);
        }
        if (nombre == null || nombre.isBlank() || nombre.strip().length() > 100) {
            throw new IllegalArgumentException("Nombre obligatorio de hasta 100 caracteres");
        }
        CatalogoAvatarCuidador.validar(avatarId);
        try {
            return transacciones.completarRegistro(acreditacion, nombre.strip(), avatarId);
        } catch (DataIntegrityViolationException ex) {
            // Translate only the known UNIQUE violation, never unrelated integrity errors.
            if (!TransactionSynchronizationManager.isActualTransactionActive() && esCorreoDuplicado(ex)) {
                throw new RechazoAutenticacion(Motivo.CUENTA_EXISTENTE);
            }
            throw ex;
        }
    }

    private static boolean esCorreoDuplicado(Throwable error) {
        for (var causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uq_cuidador_email".equals(violation.getConstraintName())
                    && violation.getSQLException().getErrorCode() == 1062) return true;
            if (causa instanceof java.sql.SQLException sql && sql.getErrorCode() == 1062
                    && sql.getMessage() != null && sql.getMessage().matches(
                        "(?s).*\\bfor key ['`](?:[^'`]+\\.)?uq_cuidador_email['`].*")) return true;
        }
        return false;
    }

    public enum Estado { AUTENTICADO, REGISTRO_PENDIENTE, RECHAZADO }
    public enum Motivo { CODIGO_INEXISTENTE, CODIGO_USADO, CODIGO_VENCIDO, LIMITE_INTENTOS,
        CODIGO_INCORRECTO, CUENTA_INACTIVA, REENVIO_PREMATURO, COMPROBANTE_INVALIDO, CUENTA_EXISTENTE }

    public record ResultadoVerificacion(Estado estado, Integer idCuidador, String comprobante, Motivo motivo) {
        public ResultadoVerificacion {
            Objects.requireNonNull(estado);
            boolean valido = switch (estado) {
                case AUTENTICADO -> idCuidador != null && comprobante == null && motivo == null;
                case REGISTRO_PENDIENTE -> idCuidador == null && comprobante != null && motivo == null;
                case RECHAZADO -> idCuidador == null && comprobante == null && motivo != null;
            };
            if (!valido) throw new IllegalArgumentException("Resultado inconsistente");
        }
        public static ResultadoVerificacion rechazado(Motivo motivo) {
            return new ResultadoVerificacion(Estado.RECHAZADO, null, null, motivo);
        }
    }

    public static class RechazoAutenticacion extends RuntimeException {
        private final Motivo motivo;
        public RechazoAutenticacion(Motivo motivo) {
            super(motivo.name());
            this.motivo = motivo;
        }
        public Motivo getMotivo() { return motivo; }
    }
}
