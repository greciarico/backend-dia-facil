package dia_facil.backend.service.auth;

@FunctionalInterface
public interface EnvioCodigoAcceso {
    void enviar(String email, String codigo);
}
