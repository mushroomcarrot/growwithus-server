package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.elevenlabs")
public class ElevenLabsProperties {
    private boolean enabled;
    private String apiKey = "";
    private String voiceId = "";
    private String model = "eleven_flash_v2_5";

    @Override public String toString() {
        return "ElevenLabsProperties[enabled=" + enabled + ", model=" + model + ", apiKey=***]";
    }
}
