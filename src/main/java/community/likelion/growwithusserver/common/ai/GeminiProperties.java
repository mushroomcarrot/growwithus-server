package community.likelion.growwithusserver.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.gemini")
public class GeminiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    // 아바타 그림을 만드는 이미지 모델. 비워 두면 아바타 생성은 기본 아바타로 대체된다
    private String imageModel = "";
    @Override public String toString() { return "GeminiProperties[enabled=" + enabled + ", model=" + model + ", imageModel=" + imageModel + ", apiKey=***]"; }
}
