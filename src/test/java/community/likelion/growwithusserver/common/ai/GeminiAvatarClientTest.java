package community.likelion.growwithusserver.common.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiAvatarClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();

    private GeminiAvatarClient client() {
        var properties = new GeminiProperties();
        properties.setApiKey("fake-test-key");
        properties.setImageModel("gemini-3.1-flash-image");
        return new GeminiAvatarClient(http.build(), properties, mapper);
    }

    private static byte[] image(int width, int height, String format) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, bytes);
        return bytes.toByteArray();
    }

    private String reply(String stepType, String blockType, String data) {
        return mapper.writeValueAsString(Map.of("steps", List.of(Map.of("type", stepType,
                "content", List.of(Map.of("type", blockType, "data", data))))));
    }

    @Test void sendsShrunkPhotoToInteractionsApiAndReturnsSquarePng() throws Exception {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(header("x-goog-api-key", "fake-test-key"))
                .andExpect(request -> {
                    var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsBytes();
                    assertEquals(body.length, request.getHeaders().getContentLength());
                    var json = mapper.readTree(body);
                    assertEquals("gemini-3.1-flash-image", json.path("model").asText());
                    assertEquals("image", json.path("input").path(0).path("type").asText());
                    assertEquals("image/jpeg", json.path("input").path(0).path("mime_type").asText());
                    // 원본(2000픽셀)이 아니라 768픽셀 이하로 줄인 사진을 보낸다
                    var sent = ImageIO.read(new ByteArrayInputStream(
                            Base64.getDecoder().decode(json.path("input").path(0).path("data").asText())));
                    assertTrue(sent.getWidth() <= 768 && sent.getHeight() <= 768);
                    assertEquals("image", json.path("response_format").path("type").asText());
                    assertFalse(new String(body, java.nio.charset.StandardCharsets.UTF_8).contains("fake-test-key"));
                })
                .andRespond(withSuccess(reply("model_output", "image",
                        Base64.getEncoder().encodeToString(image(1024, 768, "jpeg"))), MediaType.APPLICATION_JSON));

        var result = client().generate(image(2000, 1500, "png"));

        server.verify();
        assertTrue(result.isPresent());
        var avatar = ImageIO.read(new ByteArrayInputStream(result.get()));
        assertEquals(GeminiAvatarClient.SIZE, avatar.getWidth());
        assertEquals(GeminiAvatarClient.SIZE, avatar.getHeight());
        // PNG 서명
        assertEquals((byte) 0x89, result.get()[0]);
    }

    @Test void providerErrorsAndUnusableRepliesBecomeEmpty() throws Exception {
        String tiny = Base64.getEncoder().encodeToString(image(8, 8, "png"));
        String valid = Base64.getEncoder().encodeToString(image(256, 256, "png"));
        for (String body : new String[]{"{}", "null", "not json", reply("model_output", "text", "hello"),
                reply("model_output", "image", "%%%not-base64"), reply("model_output", "image", "AQID"),
                reply("model_output", "image", tiny), reply("thought", "image", valid)}) {
            var builder = RestClient.builder();
            var mock = MockRestServiceServer.bindTo(builder).build();
            mock.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var properties = new GeminiProperties();
            properties.setApiKey("fake");
            properties.setImageModel("gemini-3.1-flash-image");
            assertTrue(new GeminiAvatarClient(builder.build(), properties, mapper).generate(image(128, 128, "png")).isEmpty(), body);
            mock.verify();
        }
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("quota detail"));
        assertTrue(client().generate(image(128, 128, "png")).isEmpty());
        server.verify();
    }

    @Test void unreadablePhotoIsNotSent() {
        assertTrue(client().generate(new byte[]{1, 2, 3}).isEmpty());
        assertTrue(client().generate(null).isEmpty());
        server.verify();
    }

    @Test void rejectsMissingKeyAndEndpointInjection() {
        var properties = new GeminiProperties();
        properties.setImageModel("gemini-3.1-flash-image");
        assertThrows(IllegalArgumentException.class, () -> new GeminiAvatarClient(http.build(), properties, mapper));
        properties.setApiKey("fake");
        properties.setImageModel("../other?key=secret");
        assertThrows(IllegalArgumentException.class, () -> new GeminiAvatarClient(http.build(), properties, mapper));
    }
}
