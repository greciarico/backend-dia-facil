package dia_facil.backend.configuration;

import dia_facil.backend.web.error.ProblemasHttp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import dia_facil.backend.service.auth.SesionCuidadorService;

@Configuration(proxyBeanMethods = false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, ObjectProvider<SesionCuidadorService> sesiones) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var solicitar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/solicitar-codigo");
        var verificar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/verificar-codigo");
        var completar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/completar-registro");
        var renovar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/renovar-sesion");
        var cerrar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/cerrar-sesion");
        var sesion = paths.matcher(HttpMethod.GET, "/api/auth/cuidadores/sesion");
        var perfiles = paths.matcher(HttpMethod.GET, "/api/perfiles");
        var crearPerfil = paths.matcher(HttpMethod.POST, "/api/perfiles");
        var detallePerfil = paths.matcher(HttpMethod.GET, "/api/perfiles/{idPerfilUsuario}");
        var vinculacion = paths.matcher(HttpMethod.GET, "/api/perfiles/{idPerfilUsuario}/vinculacion");
        var renovarCodigo = paths.matcher(HttpMethod.POST, "/api/perfiles/{idPerfilUsuario}/vinculacion/renovar-codigo");
        var actividades = paths.matcher(HttpMethod.GET, "/api/perfiles/{idPerfilUsuario}/actividades");
        var crearActividad = paths.matcher(HttpMethod.POST, "/api/perfiles/{idPerfilUsuario}/actividades");
        var iconos = paths.matcher(HttpMethod.GET, "/api/iconos-actividad");
        var plantilla = paths.matcher(HttpMethod.GET, "/api/plantillas-actividad/sugerencia");
        var pictogramas = paths.matcher(HttpMethod.GET, "/api/pictogramas/arasaac");
        var ajustes = paths.matcher(HttpMethod.GET, "/api/perfiles/{idPerfilUsuario}/accesibilidad");
        var cambiarAjustes = paths.matcher(HttpMethod.PATCH, "/api/perfiles/{idPerfilUsuario}/accesibilidad");
        var restablecerAjustes = paths.matcher(HttpMethod.POST, "/api/perfiles/{idPerfilUsuario}/accesibilidad/restablecer");
        var ajustesCuidador = paths.matcher(HttpMethod.GET, "/api/cuidador/accesibilidad");
        var cambiarCuidador = paths.matcher(HttpMethod.PATCH, "/api/cuidador/accesibilidad");
        var restablecerCuidador = paths.matcher(HttpMethod.POST, "/api/cuidador/accesibilidad/restablecer");
        var privados = new org.springframework.security.web.util.matcher.OrRequestMatcher(sesion, perfiles, crearPerfil, detallePerfil, vinculacion, renovarCodigo, actividades, crearActividad, iconos, plantilla, pictogramas, ajustes, cambiarAjustes, restablecerAjustes, ajustesCuidador, cambiarCuidador, restablecerCuidador);
        var bearer = AutenticacionBearerCuidador.filtro(sesiones, mapper);
        bearer.setRequestMatcher(privados);
        return http.authorizeHttpRequests(auth -> auth.requestMatchers(solicitar, verificar, completar, renovar, cerrar).permitAll()
                .requestMatchers(sesion, perfiles, crearPerfil, detallePerfil, vinculacion, renovarCodigo, actividades, crearActividad, iconos, plantilla, pictogramas, ajustes, cambiarAjustes, restablecerAjustes, ajustesCuidador, cambiarCuidador, restablecerCuidador).hasRole("CUIDADOR")
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers(solicitar, verificar, completar, renovar, cerrar, crearPerfil, crearActividad, renovarCodigo, cambiarAjustes, restablecerAjustes, cambiarCuidador, restablecerCuidador))
            .addFilterBefore(bearer, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    if (privados.matches(request)) ProblemasHttp.escribir(response, mapper, request.getRequestURI(), 401, "SESION_REQUIERE_RENOVACION", "Verifica nuevamente tu acceso.");
                    else ProblemasHttp.accesoDenegado(mapper, request, response);
                })
                .accessDeniedHandler((request, response, error) -> ProblemasHttp.accesoDenegado(mapper, request, response)))
            .build();
    }
}
