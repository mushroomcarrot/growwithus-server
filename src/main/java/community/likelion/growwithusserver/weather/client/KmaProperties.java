package community.likelion.growwithusserver.weather.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 기상청 단기예보 조회서비스 연결 설정 (application.yml의 weather.kma.*). 명세는 docs/public-data.md를 본다.
@Data
@ConfigurationProperties(prefix = "weather.kma")
public class KmaProperties {
    // 공공데이터포털 인증키. 포털이 보여주는 인코딩된 값과 디코딩된 값 모두 받는다
    private String serviceKey;
    private String baseUrl = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    // 한 번 호출이 실패했을 때 다시 시도하는 횟수를 포함한 전체 시도 횟수
    private int maxAttempts = 3;
    // 다시 시도하기 전에 기다리는 시간
    private Duration retryDelay = Duration.ofSeconds(1);

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
