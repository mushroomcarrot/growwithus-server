package community.likelion.growwithusserver.common.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Gemini 이미지 모델(Interactions API)로 인물 사진을 캐릭터 아바타로 바꾼다.
 *
 * 1. 사진과 키를 로그에 남기지 않는다. 실패하면 비어 있는 값을 주고 다른 모델로 넘어가지 않는다.
 * 2. 보내는 사진은 768픽셀 이하 JPEG로 줄인다. 받은 그림은 그대로 쓰지 않고 512픽셀 PNG로 다시 만든다.
 */
public final class GeminiAvatarClient implements AvatarImageClient {
    public static final int SIZE = 512;
    private static final int MAX_REPLY_IMAGE = 12 * 1024 * 1024;
    private static final String PROMPT = """
            이 사진 속 인물을 따뜻한 손그림 느낌의 캐릭터 아바타로 그려 주세요.
            얼굴과 어깨가 가운데 오는 정사각형 그림, 밝은 단색 배경, 부드러운 색감으로 그립니다.
            인물의 인상과 머리 모양, 안경 같은 특징은 살리되 실제 사진처럼 보이지 않게 그립니다.
            글자, 로고, 테두리는 넣지 않습니다.
            """;

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String model;
    private final Semaphore slots = new Semaphore(1);

    public GeminiAvatarClient(RestClient external, GeminiProperties properties, ObjectMapper mapper) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getImageModel() == null || !properties.getImageModel().matches("gemini-[a-z0-9.-]{1,70}")) {
            throw new IllegalArgumentException("Gemini 이미지 모델과 서버 API 키를 설정해야 합니다.");
        }
        this.mapper = mapper;
        this.model = properties.getImageModel();
        this.http = external.mutate().baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", properties.getApiKey())
                .defaultHeader("Api-Revision", "2026-05-20").build();
    }

    @Override
    public Optional<byte[]> generate(byte[] photo) {
        if (photo == null || photo.length == 0 || photo.length > 10 * 1024 * 1024 || !slots.tryAcquire()) {
            return Optional.empty();
        }
        try {
            byte[] preview = ImagePreview.jpeg(photo);
            if (preview == null) {
                return Optional.empty();
            }
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of("type", "image", "mime_type", "image/jpeg", "data", Base64.getEncoder().encodeToString(preview)),
                            Map.of("type", "text", "text", PROMPT)),
                    "response_format", Map.of("type", "image", "mime_type", "image/png", "aspect_ratio", "1:1")));
            JsonNode response = http.post().uri("/v1beta/interactions")
                    .contentType(MediaType.APPLICATION_JSON).contentLength(body.length).body(body)
                    .retrieve().body(JsonNode.class);
            return firstImage(response).map(GeminiAvatarClient::toSquarePng);
        } catch (RuntimeException | java.io.IOException ex) {
            return Optional.empty();
        } finally {
            slots.release();
        }
    }

    // 응답의 model_output 단계에서 첫 그림을 꺼낸다. 생각 단계에 들어 있는 그림은 쓰지 않는다.
    private Optional<byte[]> firstImage(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;
            }
            for (JsonNode block : step.path("content")) {
                JsonNode data = block.path("data");
                if (!"image".equals(block.path("type").asText()) || !data.isTextual()
                        || data.asText().length() > MAX_REPLY_IMAGE / 3 * 4 + 4) {
                    continue;
                }
                try {
                    return Optional.of(Base64.getDecoder().decode(data.asText()));
                } catch (IllegalArgumentException ex) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    // 가운데를 정사각형으로 잘라 512픽셀 PNG로 만든다. 그림으로 읽히지 않으면 예외로 실패 처리된다.
    static byte[] toSquarePng(byte[] image) {
        try {
            BufferedImage decoded = javax.imageio.ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null || decoded.getWidth() < 64 || decoded.getHeight() < 64
                    || decoded.getWidth() > 8192 || decoded.getHeight() > 8192) {
                throw new AiClientException();
            }
            int side = Math.min(decoded.getWidth(), decoded.getHeight());
            int x = (decoded.getWidth() - side) / 2;
            int y = (decoded.getHeight() - side) / 2;
            BufferedImage square = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            var graphics = square.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, SIZE, SIZE);
                graphics.drawImage(decoded, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(square, "png", output)) {
                throw new AiClientException();
            }
            return output.toByteArray();
        } catch (java.io.IOException ex) {
            throw new AiClientException();
        }
    }
}
