package community.likelion.growwithusserver.demo.data.dto;

import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoWeatherResponseDto {
    private WeatherCondition condition;
    // 캐릭터 애니메이션 (sunny_happy, rain_umbrella, hot_sweat)
    private String animation;
    // 관리사 카드의 날씨 안내 문구. 작업 생성(#7)이 붙기 전까지 null
    private String weatherNotice;
    // 다시 만든 작업 수. 작업 생성(#7)이 붙기 전까지 0
    private int regeneratedTasks;
}
