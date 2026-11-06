package dia_facil.backend.configuration;

import dia_facil.backend.service.auth.*;
import dia_facil.backend.web.error.ProblemasHttp;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.NullSecurityContextRepository;
import tools.jackson.databind.ObjectMapper;

/** Uses Spring's filter/provider lifecycle; credentials never become principals. */
public final class AutenticacionBearerCuidador {
    private AutenticacionBearerCuidador() {}
    public static AuthenticationFilter filtro(ObjectProvider<SesionCuidadorService> sesiones, ObjectMapper mapper) {
        var provider = new AuthenticationProvider() {
            @Override public boolean supports(Class<?> type) { return CredencialBearer.class.isAssignableFrom(type); }
            @Override public Authentication authenticate(Authentication request) {
                var service = sesiones.getIfAvailable();
                if (service == null) throw new AuthenticationServiceException("Servicio no disponible");
                try {
                    var identidad = service.autenticar((String) request.getCredentials());
                    return new PreAuthenticatedAuthenticationToken(identidad, null, List.of(new SimpleGrantedAuthority("ROLE_CUIDADOR")));
                } catch (SesionInvalidada ex) { throw new RechazoBearer(ex.renovable()); }
                catch (org.springframework.dao.DataAccessException ex) { throw new AuthenticationServiceException("Servicio no disponible"); }
            }
        };
        var filter = new AuthenticationFilter(new ProviderManager(provider), request -> {
            var headers = Collections.list(request.getHeaders("Authorization"));
            if (headers.isEmpty()) return null;
            if (headers.size() != 1 || !headers.getFirst().matches("(?i:Bearer) a_[A-Za-z0-9_-]{43}")) throw new RechazoBearer(true);
            return new CredencialBearer(headers.getFirst().substring(7));
        });
        filter.setMfaEnabled(false);
        filter.setSecurityContextRepository(new NullSecurityContextRepository());
        filter.setSuccessHandler(new AuthenticationSuccessHandler() {
            @Override public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest r, jakarta.servlet.http.HttpServletResponse s, Authentication a) {}
            @Override public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest r, jakarta.servlet.http.HttpServletResponse s,
                    jakarta.servlet.FilterChain chain, Authentication a) throws java.io.IOException, jakarta.servlet.ServletException { chain.doFilter(r, s); }
        });
        filter.setFailureHandler((r, s, ex) -> {
            boolean unavailable = ex instanceof AuthenticationServiceException;
            boolean renewable = ex instanceof RechazoBearer rechazo && rechazo.renovable;
            ProblemasHttp.escribir(s, mapper, r.getRequestURI(), unavailable ? 503 : 401,
                unavailable ? "SERVICIO_NO_DISPONIBLE" : renewable ? "SESION_REQUIERE_RENOVACION" : "SESION_INVALIDA",
                unavailable ? "El servicio no está disponible temporalmente." : "Verifica nuevamente tu acceso.");
        });
        return filter;
    }
    private static final class RechazoBearer extends AuthenticationException {
        private final boolean renovable;
        RechazoBearer(boolean renovable) { super("Acceso no válido"); this.renovable = renovable; }
    }
    private static final class CredencialBearer extends org.springframework.security.authentication.AbstractAuthenticationToken {
        private String token;
        CredencialBearer(String token) { super(List.of()); this.token = token; }
        @Override public Object getPrincipal() { return "cuidador"; }
        @Override public Object getCredentials() { return token; }
        @Override public void eraseCredentials() { super.eraseCredentials(); token = null; }
        @Override public String toString() { return "CredencialBearer[contenido reservado]"; }
    }
}
