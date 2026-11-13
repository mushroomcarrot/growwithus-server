package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.local-stt")
public class LocalSttProperties {
    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:8765";
}
