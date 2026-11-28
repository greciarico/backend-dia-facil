package dia_facil.backend.service.fotos;

import com.cloudinary.Cloudinary;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "diafacil.cloudinary.enabled", havingValue = "true")
class CloudinaryFotosConfiguration {
    @Bean
    CloudinaryFotos cloudinaryFotos(@Value("${diafacil.cloudinary.cloud-name:}") String cloud,
        @Value("${diafacil.cloudinary.api-key:}") String key, @Value("${diafacil.cloudinary.api-secret:}") String secret,
        @Value("${diafacil.cloudinary.entorno:dev}") String entorno) {
        if (!cloud.matches("[a-zA-Z0-9_-]+") || key.isBlank() || secret.isBlank())
            throw new IllegalStateException("Configura el entorno y las credenciales privadas de Cloudinary.");
        return new CloudinaryFotos(new Cloudinary(Map.of("cloud_name", cloud, "api_key", key, "api_secret", secret, "secure", true)), entorno);
    }
}
