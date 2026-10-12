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

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var solicitar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/solicitar-codigo");
        var verificar = paths.matcher(HttpMethod.POST, "/api/auth/cuidadores/verificar-codigo");
        return http.authorizeHttpRequests(auth -> auth.requestMatchers(solicitar, verificar).permitAll()
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers(solicitar, verificar))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> ProblemasHttp.accesoDenegado(mapper, request, response))
                .accessDeniedHandler((request, response, error) -> ProblemasHttp.accesoDenegado(mapper, request, response)))
            .build();
    }
}
