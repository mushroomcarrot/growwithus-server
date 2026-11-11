package community.likelion.growwithusserver.demo.data.dto;

import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoWeatherRequestDto {
    // SUNNY, RAIN, HOT만 허용한다
    @NotNull
    private WeatherCondition condition;
}
