package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// LLM, STT, TTS 연결 설정 (application.yml의 ai.*)
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private Provider llm = new Provider();
    private Provider stt = new Provider();
    private Tts tts = new Tts();

    @Data
    public static class Provider {
        private String apiKey;
        private String baseUrl;
        private String model;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class Tts extends Provider {
        private String voice;
    }
}
