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
    // 이미지 모델을 정하지 않으면 생성하지 않고 기본 아바타로 대체한다
    @Bean public AvatarImageClient geminiAvatarImageClient(RestClient externalRestClient, GeminiProperties properties, ObjectMapper mapper) {
        return properties.getImageModel() == null || properties.getImageModel().isBlank()
                ? photo -> java.util.Optional.empty() : new GeminiAvatarClient(externalRestClient, properties, mapper);
    }
}
