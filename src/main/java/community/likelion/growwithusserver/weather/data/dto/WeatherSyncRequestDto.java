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
public class WeatherSyncRequestDto {
    // 수집할 농장의 외부 ID (farm_3). 비어 있으면 등록된 모든 농장
    private List<String> farmIds;
}
