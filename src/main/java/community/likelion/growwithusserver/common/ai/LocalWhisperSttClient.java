package community.likelion.growwithusserver.common.ai;

import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** local-ai/stt_server.py로만 호출한다. 외부 STT 서비스와 API 키는 사용하지 않는다. */
public final class LocalWhisperSttClient implements SttClient {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final RestClient restClient;

    public LocalWhisperSttClient(RestClient externalRestClient, LocalSttProperties properties) {
        LocalAiAddress.validate(properties.getBaseUrl());
        restClient = externalRestClient.mutate().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String transcribe(byte[] audio, String filename) {
        if (audio == null || audio.length == 0 || audio.length > MAX_BYTES) throw new FileNotSupportedException();
        try {
            JsonNode response = restClient.post().uri("/transcribe").contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(audio).retrieve()
                    .onStatus(status -> status.value() == 400 || status.value() == 413 || status.value() == 415,
                            (request, result) -> { throw new FileNotSupportedException(); })
                    .body(JsonNode.class);
            JsonNode text = response == null ? null : response.get("text");
            JsonNode duration = response == null ? null : response.get("durationSeconds");
            if (text == null || !text.isTextual() || text.asText().isBlank() || text.asText().length() > 8000
                    || duration == null || !duration.isNumber() || !Double.isFinite(duration.asDouble())
                    || duration.asDouble() <= 0) throw new AiClientException();
            if (duration.asDouble() > 60) throw new FileNotSupportedException();
            return text.asText();
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new AiClientException();
        }
    }
}
