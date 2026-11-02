package dia_facil.backend.configuration;

import dia_facil.backend.service.dispositivos.*;
import dia_facil.backend.web.error.ProblemasHttp;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;

@Configuration(proxyBeanMethods = false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class DispositivoSecurityConfiguration {
    @Bean @Order(0)
    public SecurityFilterChain dispositivoSecurity(HttpSecurity http, ObjectProvider<AccesoDispositivoService> servicios,
        ObjectMapper mapper, Environment env) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var prepare = paths.matcher(HttpMethod.POST, "/api/dispositivos/preparar-vinculacion");
        var bind = paths.matcher(HttpMethod.POST, "/api/dispositivos/vincular");
        var current = paths.matcher(HttpMethod.GET, "/api/dispositivos/vinculacion");
        var leave = paths.matcher(HttpMethod.DELETE, "/api/dispositivos/vinculacion");
        var agenda = paths.matcher(HttpMethod.GET, "/api/usuario/agenda");
        var accesibilidad = paths.matcher(HttpMethod.GET, "/api/usuario/accesibilidad");
        var detalle = paths.matcher(HttpMethod.GET, "/api/usuario/actividades/{idActividad}");
        var guia = paths.matcher(HttpMethod.GET,"/api/usuario/actividades/{actividad}/guia");
        var ejecucion = paths.matcher(HttpMethod.GET,"/api/usuario/actividades/{actividad}/ejecuciones/{ejecucion}");
        var iniciar = paths.matcher(HttpMethod.POST,"/api/usuario/actividades/{actividad}/guia");
        var avanzar = paths.matcher(HttpMethod.PUT,"/api/usuario/actividades/{actividad}/ejecuciones/{ejecucion}/pasos/{paso}/completar");
        var guiada = paths.matcher(HttpMethod.POST,"/api/usuario/actividades/{actividad}/ejecuciones/{ejecucion}/finalizar-guiada");
        var ayuda = paths.matcher(HttpMethod.POST,"/api/usuario/solicitudes-ayuda");
        var recuperarAyuda = paths.matcher(HttpMethod.GET,"/api/usuario/solicitudes-ayuda/por-clave/{clave}");
        var directa = paths.matcher(HttpMethod.POST,"/api/usuario/actividades/{actividad}/finalizar-directa");
        var limit = new LimiteVinculacion(Clock.systemUTC(), env.getProperty("diafacil.dispositivos.limite.minuto", Integer.class, 10),
            env.getProperty("diafacil.dispositivos.limite.hora", Integer.class, 60), env.getProperty("diafacil.dispositivos.limite.global", Integer.class, 300));
        var filtro = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                jakarta.servlet.FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
                if (prepare.matches(request) || bind.matches(request)) {
                    int wait = limit.espera(request.getRemoteAddr());
                    if (wait > 0) { response.setHeader("Retry-After", Integer.toString(wait));
                        ProblemasHttp.escribir(response, mapper, request.getRequestURI(), 429, "LIMITE_VINCULACION", "Espera antes de intentarlo nuevamente."); return; }
                }
                var service = servicios.getIfAvailable();
                if (service == null) { ProblemasHttp.escribir(response, mapper, request.getRequestURI(), 503, "SERVICIO_NO_DISPONIBLE", "El servicio no está disponible."); return; }
                // Preparation stays public, but a disabled module must not fall through to static resources.
                if (prepare.matches(request)) { chain.doFilter(request, response); return; }
                try {
                    var headers = Collections.list(request.getHeaders("Authorization"));
                    if (headers.size() != 1 || !headers.getFirst().matches("(?i:Bearer) du_[A-Za-z0-9_-]{43}")) throw RechazoDispositivo.invalida();
                    var principal = service.autenticar(headers.getFirst().substring(7));
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new PreAuthenticatedAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority(principal.idPerfilUsuario() == null ? "ROLE_VINCULACION_PENDIENTE" : "ROLE_USUARIO"))));
                    SecurityContextHolder.setContext(context);
                } catch (RechazoDispositivo ex) {
                    ProblemasHttp.escribir(response, mapper, request.getRequestURI(), ex.status(), ex.codigo(), ex.getMessage()); return;
                } catch (org.springframework.dao.DataAccessException ex) {
                    ProblemasHttp.escribir(response, mapper, request.getRequestURI(), 503, "SERVICIO_NO_DISPONIBLE", "El servicio no está disponible."); return;
                }
                try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
            }
        };
        return http.securityMatcher("/api/dispositivos/**", "/api/usuario/**")
            .authorizeHttpRequests(auth -> auth.requestMatchers(prepare).permitAll()
                .requestMatchers(bind, current).hasAnyRole("VINCULACION_PENDIENTE", "USUARIO")
                .requestMatchers(leave).hasRole("VINCULACION_PENDIENTE")
                .requestMatchers(agenda, accesibilidad, detalle, guia, ejecucion, iniciar, avanzar, guiada, directa, ayuda, recuperarAyuda).hasRole("USUARIO").anyRequest().denyAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers(prepare, bind, leave, iniciar, avanzar, guiada, directa, ayuda))
            .addFilterBefore(filtro, AnonymousAuthenticationFilter.class)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint((r,s,x) -> ProblemasHttp.escribir(s,mapper,r.getRequestURI(),401,"VINCULACION_NO_VALIDA","Conecta nuevamente tu agenda."))
                .accessDeniedHandler((r,s,x) -> ProblemasHttp.accesoDenegado(mapper,r,s))).build();
    }
}
