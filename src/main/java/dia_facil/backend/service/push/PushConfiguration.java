package dia_facil.backend.service.push;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(PushProperties.class) @EnableScheduling public class PushConfiguration {
 public PushConfiguration(PushProperties properties){if(properties.enabled()){
  try{java.util.UUID.fromString(properties.projectId());}catch(RuntimeException ex){throw new IllegalStateException("Configura EXPO_PROJECT_ID real antes de habilitar push.");}
  if(properties.accessToken()==null||properties.accessToken().isBlank())throw new IllegalStateException("Configura EXPO_PUSH_ACCESS_TOKEN privado antes de habilitar push.");
 }}
}