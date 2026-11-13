package community.likelion.growwithusserver.common.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import java.util.Map;

public final class LocalTtsClient implements TtsClient {
    private final RestClient client;
    private final tools.jackson.databind.ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    public LocalTtsClient(RestClient external, LocalTtsProperties properties) {
        LocalAiAddress.validate(properties.getBaseUrl());
        client = external.mutate().baseUrl(properties.getBaseUrl()).build();
    }
    @Override public byte[] synthesize(String text) { return synthesize(text, 0.9); }
    @Override public byte[] synthesize(String text, double speed) {
        if (text == null || text.isBlank() || text.length() > 300 || text.indexOf('\0') >= 0
                || !Double.isFinite(speed) || speed < 0.5 || speed > 1.5) throw new AiClientException();
        try {
            byte[] payload = mapper.writeValueAsBytes(Map.of("text", text, "speed", speed));
            var response = client.post().uri("/synthesize").contentType(MediaType.APPLICATION_JSON)
                    .contentLength(payload.length).body(payload).retrieve().toEntity(byte[].class);
            byte[] audio = response.getBody();
            MediaType type = response.getHeaders().getContentType();
            if (type == null || !MediaType.valueOf("audio/mpeg").isCompatibleWith(type)
                    || audio == null || audio.length < 3 || audio.length > 1024 * 1024
                    || !((audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3')
                        || ((audio[0] & 255) == 255 && (audio[1] & 224) == 224))) throw new AiClientException();
            return audio;
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new AiClientException();
        }
    }
}
