package community.likelion.growwithusserver.common.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** 로컬 Ollama 전용이다. 유료 제공자로 자동 전환하거나 API 키를 사용하지 않는다. */
public final class OllamaLlmClient implements LlmClient {
    private final RestClient restClient;
    private final String model;

    public OllamaLlmClient(RestClient externalRestClient, LocalLlmProperties properties) {
        LocalAiAddress.validate(properties.getBaseUrl());
        model = properties.getModel();
        if (model == null || model.isBlank() || model.toLowerCase(java.util.Locale.ROOT).contains("cloud")) {
            throw new IllegalArgumentException("다운로드된 로컬 모델 이름을 설정해야 합니다.");
        }
        restClient = externalRestClient.mutate().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            JsonNode response = restClient.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "stream", false, "format", "json",
                            "messages", List.of(Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)),
                            "options", Map.of("temperature", 0, "num_predict", 1024)))
                    .retrieve().body(JsonNode.class);
            JsonNode content = response == null ? null : response.path("message").get("content");
            if (content == null || !content.isTextual() || content.asText().isBlank()
                    || content.asText().length() > 16000) throw new AiClientException();
            return content.asText();
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new AiClientException();
        }
    }
}
