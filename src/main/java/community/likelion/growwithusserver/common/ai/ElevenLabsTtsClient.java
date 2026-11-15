package community.likelion.growwithusserver.common.ai;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** 기존 크레딧으로 MP3를 생성한다. 결제 설정이나 목소리 등록은 변경하지 않는다. */
public final class ElevenLabsTtsClient implements TtsClient {
    private final RestClient client;
    private final ElevenLabsProperties properties;
    private final TtsClient offline;
    private final Semaphore slots = new Semaphore(2);
    private final JsonMapper mapper = JsonMapper.builder().build();

    public static TtsClient select(RestClient external, ElevenLabsProperties properties, TtsClient offline) {
        if (!properties.isEnabled()) return offline;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build());
        // 프론트의 10초 제한 전에 실패를 반환해 브라우저 읽어주기로 전환한다.
        factory.setReadTimeout(Duration.ofSeconds(8));
        return new ElevenLabsTtsClient(external.mutate().requestFactory(factory).build(), properties, offline);
    }

    public ElevenLabsTtsClient(RestClient external, ElevenLabsProperties properties, TtsClient offline) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getApiKey().contains("\n") || properties.getApiKey().contains("\r")
                || properties.getVoiceId() == null || !properties.getVoiceId().matches("[A-Za-z0-9]{20}")
                || properties.getModel() == null || !properties.getModel().matches("eleven_[a-z0-9_]{1,60}")
                || !Double.isFinite(properties.getSpeedScale()) || properties.getSpeedScale() < 0.5 || properties.getSpeedScale() > 2.0) {
            throw new IllegalArgumentException("ElevenLabs 키, 목소리 ID, 모델과 속도 배율 설정을 확인하세요.");
        }
        this.properties = properties;
        this.offline = offline;
        this.client = external.mutate().baseUrl("https://api.elevenlabs.io").build();
    }

    @Override public byte[] synthesize(String text) { return synthesize(text, 0.9); }

    @Override public byte[] synthesize(String text, double speed) {
        if (text == null || text.isBlank() || text.length() > 300 || text.indexOf('\0') >= 0
                || !Double.isFinite(speed) || speed < 0.5 || speed > 1.5) throw new AiClientException();
        // 제공자의 속도 범위 밖에서는 기존 생성기로 정확한 요청 속도를 유지한다.
        if (speed < 0.7 || speed > 1.2) return offline.synthesize(text, speed);
        if (!slots.tryAcquire()) throw new AiClientException();
        try {
            // 요청 속도는 화면 기준값이다. 이 목소리는 말이 느려 배율을 곱한 뒤 제공자 범위로 맞춘다.
            double pace = Math.round(Math.max(0.7, Math.min(1.2, speed * properties.getSpeedScale())) * 100) / 100.0;
            var body = new java.util.LinkedHashMap<String, Object>();
            // 구역 번호, 숫자와 영문 약어는 한국어 발음으로 풀어 보낸다.
            body.put("text", KoreanSpeechText.normalize(text));
            body.put("model_id", properties.getModel());
            // 언어 지정은 v2.5 계열만 받는다. 다른 모델에 보내면 요청이 거절된다.
            if (properties.getModel().matches("eleven_(flash|turbo)_v2_5")) body.put("language_code", "ko");
            body.put("voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75,
                    "style", 0.0, "speed", pace, "use_speaker_boost", true));
            byte[] payload = mapper.writeValueAsBytes(body);
            return client.post().uri("/v1/text-to-speech/{voice}?output_format=mp3_44100_128", properties.getVoiceId())
                    .header("xi-api-key", properties.getApiKey()).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg")).contentLength(payload.length).body(payload)
                    .exchange((request, response) -> {
                        var type = response.getHeaders().getContentType();
                        if (!response.getStatusCode().is2xxSuccessful() || type == null
                                || !MediaType.valueOf("audio/mpeg").isCompatibleWith(type)) throw new AiClientException();
                        byte[] audio = response.getBody().readNBytes(1024 * 1024 + 1);
                        if (audio.length < 3 || audio.length > 1024 * 1024
                                || !((audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3')
                                || ((audio[0] & 255) == 255 && (audio[1] & 224) == 224))) throw new AiClientException();
                        return audio;
                    });
        } catch (RuntimeException ex) {
            // 제공자 본문과 인증 헤더를 예외 또는 로그에 담지 않는다. 자동 유료 재시도도 하지 않는다.
            throw new AiClientException();
        } finally {
            slots.release();
        }
    }
}
