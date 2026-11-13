package community.likelion.growwithusserver.common.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    private GeminiClient client() {
        var properties = new GeminiProperties(); properties.setApiKey("fake-test-key");
        return new GeminiClient(http.build(), properties, mapper, bytes -> new byte[]{1, 2, 3});
    }
    private String response(String text, String reason) {
        return mapper.writeValueAsString(java.util.Map.of("candidates", java.util.List.of(java.util.Map.of(
                "finishReason", reason, "content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", text)))))));
    }
    @Test void usesServerHeaderFixedEndpointAndLengthDelimitedJson() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(header("x-goog-api-key", "fake-test-key"))
                .andExpect(request -> {
                    var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsBytes();
                    assertEquals(body.length, request.getHeaders().getContentLength());
                    var json = mapper.readTree(body);
                    assertEquals("application/json", json.path("generationConfig").path("responseMimeType").asText());
                    assertFalse(new String(body, java.nio.charset.StandardCharsets.UTF_8).contains("fake-test-key"));
                }).andRespond(withSuccess(response("{\"summary\":\"ok\"}", "STOP"), MediaType.APPLICATION_JSON));
        assertEquals("{\"summary\":\"ok\"}", client().complete("system", "input")); server.verify();
    }
    @Test void transcribesNormalizedAudioAndRejectsBlankTranscript() {
        server.expect(anything()).andExpect(content().string(org.hamcrest.Matchers.containsString("audio/wav")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AQID")))
                .andRespond(withSuccess(response("{\"transcript\":\"물을 줬어요\"}", "STOP"), MediaType.APPLICATION_JSON));
        assertEquals("물을 줬어요", client().transcribe(new byte[]{4}, "memo.webm")); server.verify();
    }
    @Test void rateLimitDoesNotRetryOrExposeProviderDetails() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("secret provider detail fake-test-key"));
        var failure = assertThrows(AiClientException.class, () -> client().complete("system", "input"));
        assertFalse(String.valueOf(failure).contains("fake-test-key")); assertNull(failure.getCause()); server.verify();
    }
    @Test void rejectsBlockedTruncatedAndInvalidResponses() {
        for (String body : new String[]{"{}", "null", "not json", response("{}", "MAX_TOKENS"), response("", "STOP")}) {
            var builder = RestClient.builder(); var mock = MockRestServiceServer.bindTo(builder).build();
            mock.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var props = new GeminiProperties(); props.setApiKey("fake");
            var service = new GeminiClient(builder.build(), props, mapper, bytes -> bytes);
            assertThrows(AiClientException.class, () -> service.complete("system", "input")); mock.verify();
        }
    }
    @Test void rejectsMalformedTranscriptWithoutInventingText() {
        for (String text : new String[]{"null", "{}", "{\"transcript\":\" \"}", "{\"transcript\":123}"}) {
            var builder = RestClient.builder(); var mock = MockRestServiceServer.bindTo(builder).build();
            mock.expect(anything()).andRespond(withSuccess(response(text, "STOP"), MediaType.APPLICATION_JSON));
            var props = new GeminiProperties(); props.setApiKey("fake");
            assertThrows(AiClientException.class, () -> new GeminiClient(builder.build(), props, mapper, b -> b)
                    .transcribe(new byte[]{1}, "a.wav")); mock.verify();
        }
    }
    @Test void distinguishesUnmatchedPhotoFromUnavailableModel() throws Exception {
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
        server.expect(anything()).andRespond(withSuccess(response("{\"matched\":false,\"message\":\"작업을 확인하기 어려워요\"}", "STOP"), MediaType.APPLICATION_JSON));
        var result = client().check(bytes.toByteArray(), "물 주기");
        assertTrue(result.isPresent()); assertFalse(result.get().matched()); server.verify();
        assertTrue(client().check(new byte[]{1, 2}, "물 주기").isEmpty());
    }
    @Test void rejectsMissingKeyAndEndpointInjection() {
        var props = new GeminiProperties();
        assertThrows(IllegalArgumentException.class, () -> new GeminiClient(http.build(), props, mapper, b -> b));
        props.setApiKey("fake"); props.setModel("../other?key=secret");
        assertThrows(IllegalArgumentException.class, () -> new GeminiClient(http.build(), props, mapper, b -> b));
        assertFalse(props.toString().contains("fake"));
    }
}
