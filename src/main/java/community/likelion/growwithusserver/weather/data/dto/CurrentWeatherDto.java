package community.likelion.growwithusserver.weather.data.dto;

import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// 현재 날씨. 홈의 weather와 같은 필드다 (condition, temperature, rainProbability, updatedAt).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentWeatherDto {
    private WeatherCondition condition;
    private Double temperature;
    private Integer rainProbability;
    private OffsetDateTime updatedAt;
}
