package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.local")
public class LocalLlmProperties {
    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:11434";
    private String model = "";
    private String visionModel = "";
}
