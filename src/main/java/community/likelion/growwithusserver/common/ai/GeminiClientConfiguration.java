package community.likelion.growwithusserver.common.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name = "ai.gemini.enabled", havingValue = "true")
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiClientConfiguration {
    @Bean public GeminiClient geminiClient(RestClient externalRestClient, GeminiProperties properties, ObjectMapper mapper) {
        return new GeminiClient(externalRestClient, properties, mapper, new FfmpegAudioNormalizer()::normalize);
    }
    @Bean public TtsClient cloudModeTtsClient() { return new OfflineTtsClient(); }
}
