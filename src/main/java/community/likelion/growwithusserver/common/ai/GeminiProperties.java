package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.gemini")
public class GeminiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    @Override public String toString() { return "GeminiProperties[enabled=" + enabled + ", model=" + model + ", apiKey=***]"; }
}
