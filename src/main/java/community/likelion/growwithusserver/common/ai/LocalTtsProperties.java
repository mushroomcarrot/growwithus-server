package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.local-tts")
public class LocalTtsProperties {
    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:8766";
}
