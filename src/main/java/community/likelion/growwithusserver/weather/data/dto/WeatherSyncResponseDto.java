package community.likelion.growwithusserver.weather.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSyncResponseDto {
    // 실황과 예보를 모두 받아 저장한 농장 수
    private int syncedFarms;
    // 실황이나 예보 중 하나라도 받지 못한 농장 (farm_3, 기본 격자면 grid_64_119)
    private List<String> failedFarms;
}
