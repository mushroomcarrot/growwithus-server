package community.likelion.growwithusserver.common.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ElevenLabsTtsClientTest {
    private static final String VOICE = "0123456789abcdefghij";
    private static final byte[] AUDIO = {'I', 'D', '3', 1};
    private ElevenLabsProperties properties() {
        var p = new ElevenLabsProperties(); p.setEnabled(true); p.setApiKey("fake-key"); p.setVoiceId(VOICE); return p;
    }
    private ElevenLabsTtsClient client(RestClient.Builder builder) {
        return new ElevenLabsTtsClient(builder.build(), properties(), text -> { throw new AssertionError("Unexpected offline synthesis"); });
    }

    @Test void postsKoreanTextAndSpeedWithSecretHeaderAndExplicitLength() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.elevenlabs.io/v1/text-to-speech/" + VOICE + "?output_format=mp3_44100_128"))
                .andExpect(header("xi-api-key", "fake-key"))
                .andExpect(header("Content-Length", org.hamcrest.Matchers.notNullValue()))
                .andExpect(request -> {
                    var json = new tools.jackson.databind.ObjectMapper().readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString(java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals("오늘 텃밭에 물을 줬어요.", json.path("text").asText());
                    assertEquals("eleven_flash_v2_5", json.path("model_id").asText());
                    assertEquals(0.9, json.path("voice_settings").path("speed").asDouble());
                })
                .andRespond(withSuccess(AUDIO, MediaType.valueOf("audio/mpeg")));
        assertArrayEquals(AUDIO, client(builder).synthesize("오늘 텃밭에 물을 줬어요.")); server.verify();
    }

    @Test void providerErrorsAreNotRetriedAndDoNotExposeResponseSecrets() {
        for (var status : new HttpStatus[]{HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE}) {
            var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(withStatus(status).body("provider-private-details fake-key"));
            var ex = assertThrows(AiClientException.class, () -> client(builder).synthesize("잎을 확인했어요."));
            assertNull(ex.getCause()); assertFalse(String.valueOf(ex.getMessage()).contains("fake-key")); server.verify();
        }
    }

    @Test void rejectsMalformedOrOversizedAudioBeforeCaching() {
        for (byte[] bytes : new byte[][]{new byte[0], "not-mp3".getBytes(), new byte[1024 * 1024 + 1]}) {
            var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(withSuccess(bytes, MediaType.valueOf("audio/mpeg")));
            assertThrows(AiClientException.class, () -> client(builder).synthesize("안녕하세요")); server.verify();
        }
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(AUDIO, MediaType.APPLICATION_JSON));
        assertThrows(AiClientException.class, () -> client(builder).synthesize("안녕하세요")); server.verify();
    }

    @Test void keepsExistingExtremeSpeedSupportWithoutSendingUnsupportedProviderRequests() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var calls = new AtomicInteger();
        TtsClient offline = new TtsClient() {
            public byte[] synthesize(String text) { throw new AssertionError(); }
            public byte[] synthesize(String text, double speed) { assertTrue(speed == 0.5 || speed == 1.5); calls.incrementAndGet(); return AUDIO; }
        };
        var client = new ElevenLabsTtsClient(builder.build(), properties(), offline);
        assertArrayEquals(AUDIO, client.synthesize("느리게 읽어요", 0.5));
        assertArrayEquals(AUDIO, client.synthesize("빠르게 읽어요", 1.5));
        assertEquals(2, calls.get()); server.verify();
        for (double speed : new double[]{Double.NaN, 0.49, 1.51}) assertThrows(AiClientException.class, () -> client.synthesize("텍스트", speed));
        assertThrows(AiClientException.class, () -> client.synthesize(" "));
        assertThrows(AiClientException.class, () -> client.synthesize("가".repeat(301)));
        assertThrows(AiClientException.class, () -> client.synthesize("가\0나"));
    }

    @Test void failsStartupForMissingSettingsWithoutPuttingKeyInExceptionOrToString() {
        var p = properties(); p.setVoiceId("../../other-host");
        var ex = assertThrows(IllegalArgumentException.class, () -> new ElevenLabsTtsClient(RestClient.create(), p, text -> AUDIO));
        assertFalse(ex.getMessage().contains("fake-key")); assertFalse(p.toString().contains("fake-key"));
        p.setVoiceId(VOICE); p.setApiKey("");
        assertThrows(IllegalArgumentException.class, () -> new ElevenLabsTtsClient(RestClient.create(), p, text -> AUDIO));
    }

    @Test void limitsConcurrentRequestsWithoutWaitingAndReleasesSlotsAfterErrors() throws Exception {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var started = new java.util.concurrent.CountDownLatch(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), anything()).andRespond(request -> {
            started.countDown();
            try { assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
            return withStatus(HttpStatus.SERVICE_UNAVAILABLE).createResponse(request);
        });
        server.expect(anything()).andRespond(withSuccess(AUDIO, MediaType.valueOf("audio/mpeg")));
        var client = client(builder);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> assertThrows(AiClientException.class, () -> client.synthesize("첫 번째")));
            var second = pool.submit(() -> assertThrows(AiClientException.class, () -> client.synthesize("두 번째")));
            try {
                assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
                assertThrows(AiClientException.class, () -> client.synthesize("대기 없이 대체"));
            } finally { release.countDown(); }
            first.get(3, java.util.concurrent.TimeUnit.SECONDS); second.get(3, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertArrayEquals(AUDIO, client.synthesize("다음 요청은 정상 처리")); server.verify();
    }
}
