package community.likelion.growwithusserver.harvest.data.dto;

import community.likelion.growwithusserver.harvest.data.enums.OutingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutingResponseDto {
    private OutingType type;
    private String name;
    // 농장에서의 직선 거리 (km, 소수 첫째 자리). 농장 위치를 모르면 null
    private Double distanceKm;
    private String address;
    private String description;
}
