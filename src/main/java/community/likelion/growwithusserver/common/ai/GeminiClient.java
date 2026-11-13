package community.likelion.growwithusserver.common.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;

/** 원문, 사진과 키를 로그에 남기지 않는다. 유료 모델이나 로컬 모델로 자동 전환하지 않는다. */
public final class GeminiClient implements LlmClient, SttClient, VisionClient {
    private final RestClient http;
    private final ObjectMapper mapper;
    private final UnaryOperator<byte[]> normalize;
    private final String model;
    private final Semaphore slots = new Semaphore(2);

    public GeminiClient(RestClient external, GeminiProperties properties, ObjectMapper mapper, UnaryOperator<byte[]> normalize) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getModel() == null || !properties.getModel().matches("gemini-[a-z0-9.-]{1,70}")) {
            throw new IllegalArgumentException("Gemini 모델과 서버 API 키를 설정해야 합니다.");
        }
        this.mapper = mapper; this.normalize = normalize; this.model = properties.getModel();
        this.http = external.mutate().baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", properties.getApiKey()).build();
    }

    @Override public String complete(String system, String input) {
        if (system == null || input == null || system.length() > 16000 || input.length() > 64000) throw new AiClientException();
        return generate(system, List.of(Map.of("text", input)));
    }
    @Override public String transcribe(byte[] audio, String filename) {
        byte[] wave = normalize.apply(audio);
        String reply = generate("""
                녹음의 실제 한국어 발화만 받아 적으세요. 작업이나 관찰을 추측해 추가하지 마세요.
                발화가 없으면 transcript를 빈 문자열로 반환하세요.
                JSON {"transcript":"실제 발화"}만 반환하세요.
                """, List.of(Map.of("inlineData", Map.of("mimeType", "audio/wav", "data", Base64.getEncoder().encodeToString(wave)))));
        try {
            var root = mapper.readTree(reply);
            if (root == null) throw new AiClientException();
            var value = root.path("transcript");
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 8000) throw new AiClientException();
            return value.asText();
        } catch (tools.jackson.core.JacksonException ex) { throw new AiClientException(); }
    }
    @Override public Optional<Check> check(byte[] image, String description) {
        if (image == null || image.length == 0 || image.length > 10 * 1024 * 1024
                || description == null || description.isBlank() || description.length() > 2000) return Optional.empty();
        try {
            byte[] preview = ImagePreview.jpeg(image);
            if (preview == null) return Optional.empty();
            String reply = generate("""
                    사진과 주어진 텃밭 작업의 관련 여부를 눈에 보이는 근거로만 판단하세요.
                    사진으로 물 주기 완료나 병을 확정하지 마세요. 한국어로 짧게 설명하세요.
                    JSON {"matched":true,"message":"사진에서 확인한 내용"}만 반환하세요.
                    """, List.of(Map.of("text", description), Map.of("inlineData", Map.of("mimeType", "image/jpeg",
                    "data", Base64.getEncoder().encodeToString(preview)))));
            var root = mapper.readTree(reply);
            if (root == null || !root.path("matched").isBoolean() || !root.path("message").isTextual()) return Optional.empty();
            String message = root.path("message").asText();
            return message.isBlank() || message.length() > 200 ? Optional.empty()
                    : Optional.of(new Check(root.path("matched").asBoolean(), message));
        } catch (AiClientException | java.io.IOException | tools.jackson.core.JacksonException ex) { return Optional.empty(); }
    }

    private String generate(String system, List<Map<String, Object>> parts) {
        if (!slots.tryAcquire()) throw new AiClientException();
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                    "contents", List.of(Map.of("role", "user", "parts", parts)),
                    "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0, "maxOutputTokens", 4096)));
            JsonNode response = http.post().uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON).contentLength(body.length).body(body).retrieve().body(JsonNode.class);
            if (response == null) throw new AiClientException();
            var candidate = response.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) throw new AiClientException();
            StringBuilder text = new StringBuilder();
            for (var part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) text.append(part.path("text").asText());
                if (text.length() > 16000) throw new AiClientException();
            }
            if (text.isEmpty()) throw new AiClientException();
            return text.toString();
        } catch (org.springframework.web.client.RestClientException | tools.jackson.core.JacksonException ex) {
            throw new AiClientException();
        } finally { slots.release(); }
    }
}
