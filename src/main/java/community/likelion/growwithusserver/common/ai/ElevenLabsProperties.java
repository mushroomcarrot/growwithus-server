package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.elevenlabs")
public class ElevenLabsProperties {
    private boolean enabled;
    private String apiKey = "";
    private String voiceId = "";
    private String model = "eleven_multilingual_v2";
    // POST /tts의 요청 속도에 곱하는 배율. 결과는 제공자 범위 0.7부터 1.2로 맞춘다.
    private double speedScale = 1.2;

    @Override public String toString() {
        return "ElevenLabsProperties[enabled=" + enabled + ", model=" + model + ", apiKey=***]";
    }
}
