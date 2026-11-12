package community.likelion.growwithusserver.common.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 제공자별 구현체(@Component)가 등록되기 전까지 쓰는 기본 구현.
// 호출하면 항상 AiClientException을 던지므로, 규칙 기반 대체 로직을 먼저 개발하고 시험할 수 있다.
@Slf4j
@Configuration
@EnableConfigurationProperties({AiProperties.class, LocalLlmProperties.class, LocalSttProperties.class})
public class AiClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(VisionClient.class)
    public VisionClient visionClient(org.springframework.web.client.RestClient externalRestClient,
                                     LocalLlmProperties properties, tools.jackson.databind.ObjectMapper mapper) {
        return properties.isEnabled() && !properties.getVisionModel().isBlank()
                ? new OllamaVisionClient(externalRestClient, properties, mapper) : (image, task) -> java.util.Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient unconfiguredLlmClient(org.springframework.web.client.RestClient externalRestClient,
                                          LocalLlmProperties localProperties) {
        if (localProperties.isEnabled()) {
            return new OllamaLlmClient(externalRestClient, localProperties);
        }
        return (systemPrompt, userPrompt) -> {
            log.warn("[complete] LLM 구현체가 등록되지 않아 호출 실패 처리");
            throw new AiClientException();
        };
    }

    @Bean
    @ConditionalOnMissingBean(SttClient.class)
    public SttClient unconfiguredSttClient(org.springframework.web.client.RestClient externalRestClient,
                                          LocalSttProperties localSttProperties) {
        if (localSttProperties.isEnabled()) {
            return new LocalWhisperSttClient(externalRestClient, localSttProperties);
        }
        return (audio, filename) -> {
            log.warn("[transcribe] STT 구현체가 등록되지 않아 호출 실패 처리");
            throw new AiClientException();
        };
    }

    @Bean
    @ConditionalOnMissingBean(TtsClient.class)
    public TtsClient unconfiguredTtsClient() {
        return text -> {
            log.warn("[synthesize] TTS 구현체가 등록되지 않아 호출 실패 처리");
            throw new AiClientException();
        };
    }
}
