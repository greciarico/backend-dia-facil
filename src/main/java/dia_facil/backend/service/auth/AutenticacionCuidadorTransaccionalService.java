package dia_facil.backend.service.auth;

import dia_facil.backend.persistence.entity.CodigoAcceso;
import dia_facil.backend.persistence.entity.Cuidador;
import dia_facil.backend.persistence.enums.EstadoCuidador;
import dia_facil.backend.persistence.repository.CodigoAccesoRepository;
import dia_facil.backend.persistence.repository.CuidadorRepository;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;
import static dia_facil.backend.service.auth.AutenticacionCuidadorService.*;

/** Must be called through a Spring transactional proxy; REQUIRED / DEFAULT are deliberate. */
public class AutenticacionCuidadorTransaccionalService {
    private final CuidadorRepository cuidadores;
    private final CodigoAccesoRepository codigos;
    private final CriptografiaAutenticacion criptografia;
    private final SesionCuidadorService sesiones;

    public AutenticacionCuidadorTransaccionalService(CuidadorRepository cuidadores,
            CodigoAccesoRepository codigos, CriptografiaAutenticacion criptografia) {
        this(cuidadores, codigos, criptografia, null);
    }
    public AutenticacionCuidadorTransaccionalService(CuidadorRepository cuidadores,
            CodigoAccesoRepository codigos, CriptografiaAutenticacion criptografia, SesionCuidadorService sesiones) {
        this.cuidadores = Objects.requireNonNull(cuidadores);
        this.codigos = Objects.requireNonNull(codigos);
        this.criptografia = Objects.requireNonNull(criptografia);
        this.sesiones = sesiones;
    }

    @Transactional
    public VerificacionConSesion verificarConSesion(String email, String codigo) {
        var result = verificarCodigo(email, codigo);
        return new VerificacionConSesion(result, result.estado() == Estado.AUTENTICADO
            ? Objects.requireNonNull(sesiones).emitir(result.idCuidador()) : null);
    }
    @Transactional
    public SesionCuidadorService.Concesion registrarConSesion(CriptografiaAutenticacion.Comprobante comprobante, String nombre, String avatarId) {
        var id = completarRegistro(comprobante, nombre, avatarId);
        return Objects.requireNonNull(sesiones).emitir(id);
    }
    public record VerificacionConSesion(ResultadoVerificacion resultado, SesionCuidadorService.Concesion concesion) {
        @Override public String toString() { return "VerificacionConSesion[contenido reservado]"; }
    }

    @Transactional
    public Emision emitirCodigo(String email) {
        var ultimo = codigos.findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc(email);
        var ahora = criptografia.ahora();
        if (ultimo.isPresent() && ahora.isBefore(ultimo.get().getFechaCreacion().plusSeconds(PoliticaCodigoAcceso.REENVIO_EN_SEGUNDOS))) {
            throw new RechazoAutenticacion(Motivo.REENVIO_PREMATURO);
        }
        var codigo = criptografia.generarCodigo();
        codigos.save(CodigoAcceso.emitir(email, criptografia.hashCodigo(email, codigo), ahora.plusMinutes(10)));
        return new Emision(email, codigo);
    }

    @Transactional
    public ResultadoVerificacion verificarCodigo(String email, String codigo) {
        var ultimo = codigos.findFirstByEmailOrderByFechaCreacionDescIdCodigoAccesoDesc(email);
        if (ultimo.isEmpty()) return ResultadoVerificacion.rechazado(Motivo.CODIGO_INEXISTENTE);
        var registro = ultimo.get();
        if (registro.getUsado()) return ResultadoVerificacion.rechazado(Motivo.CODIGO_USADO);
        if (!criptografia.ahora().isBefore(registro.getFechaExpiracion())) {
            return ResultadoVerificacion.rechazado(Motivo.CODIGO_VENCIDO);
        }
        if (registro.getIntentos() >= 5) return ResultadoVerificacion.rechazado(Motivo.LIMITE_INTENTOS);
        if (!criptografia.coincideCodigo(email, codigo, registro.getCodigoHash())) {
            registro.setIntentos(registro.getIntentos() + 1);
            codigos.save(registro);
            return ResultadoVerificacion.rechazado(registro.getIntentos() == 5
                ? Motivo.LIMITE_INTENTOS : Motivo.CODIGO_INCORRECTO);
        }
        var cuidador = cuidadores.findByEmail(email);
        ResultadoVerificacion resultado;
        if (cuidador.isEmpty()) {
            resultado = new ResultadoVerificacion(Estado.REGISTRO_PENDIENTE, null,
                criptografia.emitirComprobante(email, registro.getIdCodigoAcceso()), null);
        } else if (cuidador.get().getEstado() == EstadoCuidador.INACTIVO) {
            resultado = ResultadoVerificacion.rechazado(Motivo.CUENTA_INACTIVA);
        } else {
            resultado = new ResultadoVerificacion(Estado.AUTENTICADO, cuidador.get().getIdCuidador(), null, null);
        }
        registro.setUsado(true);
        codigos.save(registro);
        return resultado;
    }

    @Transactional
    public Integer completarRegistro(CriptografiaAutenticacion.Comprobante comprobante, String nombre) {
        return completarRegistro(comprobante, nombre, null);
    }

    @Transactional
    public Integer completarRegistro(CriptografiaAutenticacion.Comprobante comprobante, String nombre, String avatarId) {
        CatalogoAvatarCuidador.validar(avatarId);
        if (!criptografia.vigente(comprobante)) throw new RechazoAutenticacion(Motivo.COMPROBANTE_INVALIDO);
        var registro = codigos.findById(comprobante.idCodigo());
        if (registro.isEmpty() || !registro.get().getUsado()
                || !registro.get().getEmail().equals(comprobante.email())) {
            throw new RechazoAutenticacion(Motivo.COMPROBANTE_INVALIDO);
        }
        if (cuidadores.findByEmail(comprobante.email()).isPresent()) {
            throw new RechazoAutenticacion(Motivo.CUENTA_EXISTENTE);
        }
        var cuidador = Cuidador.crear(comprobante.email(), nombre);
        cuidador.setAvatarId(avatarId);
        return cuidadores.saveAndFlush(cuidador).getIdCuidador();
    }

    // This value is internal and must never be logged or exposed as an HTTP response.
    public record Emision(String email, String codigo) {
        @Override public String toString() { return "Emision[contenido reservado]"; }
    }
}
