package dia_facil.backend.service.push;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("diafacil.push") public record PushProperties(boolean enabled,String projectId,String accessToken) {
 @Override public String toString(){return "PushProperties[enabled="+enabled+", projectId="+projectId+", accessToken=<redacted>]";}
}