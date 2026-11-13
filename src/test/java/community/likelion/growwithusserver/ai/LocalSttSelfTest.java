package community.likelion.growwithusserver.ai;

import community.likelion.growwithusserver.common.ai.AiClientConfiguration;
import community.likelion.growwithusserver.common.ai.AiClientException;
import community.likelion.growwithusserver.common.ai.LocalSttProperties;
import community.likelion.growwithusserver.common.ai.LocalWhisperSttClient;
import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class LocalSttSelfTest {
    private static int checks;

    public static void main(String[] args) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalSttProperties properties = new LocalSttProperties();
        LocalWhisperSttClient client = new LocalWhisperSttClient(builder.build(), properties);
        byte[] audio = {1, 2, 3};
        expect(server).andExpect(method(HttpMethod.POST)).andExpect(LocalSttSelfTest::checkBody)
                .andRespond(withSuccess("{\"text\":\"오늘 물을 줬어요\",\"durationSeconds\":3.5}", MediaType.APPLICATION_JSON));
        check(client.transcribe(audio, "recording.webm").equals("오늘 물을 줬어요"), "음성 원문 반환");
        server.verify();
        server.reset();
        rejectsFile(() -> client.transcribe(new byte[0], "empty.wav"));
        rejectsFile(() -> client.transcribe(new byte[20 * 1024 * 1024 + 1], "large.webm"));
        expect(server).andRespond(withSuccess("{\"text\":\"원문\",\"durationSeconds\":61}", MediaType.APPLICATION_JSON));
        rejectsFile(() -> client.transcribe(audio, "long.mp4"));
        server.verify();
        server.reset();
        for (String body : new String[] {"{}", "null", "{\"text\":\"\",\"durationSeconds\":3}",
                "{\"text\":\"원문\",\"durationSeconds\":-1}", "{\"text\":\"원문\",\"durationSeconds\":\"3\"}"}) {
            expect(server).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            rejectsAi(() -> client.transcribe(audio, "a.m4a"));
            server.verify();
            server.reset();
        }
        expect(server).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));
        rejectsFile(() -> client.transcribe(audio, "invalid.webm"));
        server.verify();
        server.reset();
        expect(server).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        rejectsAi(() -> client.transcribe(audio, "busy.wav"));
        server.verify();
        properties.setBaseUrl("https://remote.example");
        boolean remoteRejected = false;
        try { new LocalWhisperSttClient(builder.build(), properties); }
        catch (IllegalArgumentException expected) { remoteRejected = true; }
        check(remoteRejected, "원격 주소 거부");
        properties.setBaseUrl("http://127.0.0.1:8765");
        rejectsAi(() -> new AiClientConfiguration().unconfiguredSttClient(builder.build(), properties)
                .transcribe(audio, "a.wav"));
        properties.setEnabled(true);
        check(new AiClientConfiguration().unconfiguredSttClient(builder.build(), properties) instanceof LocalWhisperSttClient,
                "활성화 시 로컬 STT 선택");
        System.out.println("Local STT checks passed: " + checks);
    }

    private static org.springframework.test.web.client.ResponseActions expect(MockRestServiceServer server) {
        return server.expect(requestTo("http://127.0.0.1:8765/transcribe"));
    }

    private static void checkBody(ClientHttpRequest request) {
        check(MediaType.APPLICATION_OCTET_STREAM.equals(request.getHeaders().getContentType()), "바이너리 타입");
        check(java.util.Arrays.equals(((MockClientHttpRequest) request).getBodyAsBytes(), new byte[] {1, 2, 3}),
                "음성 바이트 보존");
    }

    private static void rejectsFile(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (FileNotSupportedException expected) { rejected = true; }
        check(rejected, "파일 형식 또는 길이 오류");
    }

    private static void rejectsAi(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (AiClientException expected) { rejected = true; }
        check(rejected, "음성 인식 실패");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
