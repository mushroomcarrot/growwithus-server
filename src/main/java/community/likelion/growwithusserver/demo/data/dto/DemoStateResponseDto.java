package community.likelion.growwithusserver.demo.data.dto;

import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GET /demo/state와 POST /demo/reset이 함께 쓴다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoStateResponseDto {
    // 시연 날씨. 덮어쓴 적이 없으면 null (실제 날씨를 쓰는 중)
    private WeatherCondition weather;
    // 시연 텃밭이 없으면 단계와 작업 수는 null. 빨리 감기 일수는 아직 미구현
    private String stage;
    private String stageProgress;
    private Integer fastForwardDays;
    private Integer todayTaskGardens;
}
