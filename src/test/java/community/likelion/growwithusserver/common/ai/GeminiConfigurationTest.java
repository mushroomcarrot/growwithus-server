package community.likelion.growwithusserver.common.ai;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GeminiConfigurationTest {
    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.registerBean("externalRestClient", RestClient.class, () -> RestClient.create());
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.register(AiClientConfiguration.class, GeminiClientConfiguration.class);
        return context;
    }
    @Test void cloudWinsEvenWhenLegacyLocalFlagsRemainEnabled() {
        try (var context = context(Map.of("ai.gemini.enabled", "true", "ai.gemini.api-key", "fake",
                "ai.local.enabled", "true", "ai.local-stt.enabled", "true", "ai.local-tts.enabled", "true"))) {
            context.refresh();
            assertInstanceOf(GeminiClient.class, context.getBean(LlmClient.class));
            assertSame(context.getBean(LlmClient.class), context.getBean(SttClient.class));
            assertSame(context.getBean(LlmClient.class), context.getBean(VisionClient.class));
            assertInstanceOf(OfflineTtsClient.class, context.getBean(TtsClient.class));
        }
    }
    @Test void noKeyFailsStartupInsteadOfSilentlyUsingLocalModels() {
        try (var context = context(Map.of("ai.gemini.enabled", "true"))) {
            assertThrows(org.springframework.beans.BeansException.class, context::refresh);
        }
    }
    @Test void disabledCloudKeepsExistingProviderSelection() {
        try (var context = context(Map.of("ai.gemini.enabled", "false"))) {
            context.refresh();
            assertFalse(context.getBean(LlmClient.class) instanceof GeminiClient);
            assertThrows(AiClientException.class, () -> context.getBean(LlmClient.class).complete("system", "text"));
        }
    }
}
