package community.likelion.growwithusserver.farm.data.dto;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.enums.District;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmItemDto {
    private String farmId;
    private String name;
    private District district;
    private String address;
    private double latitude;
    private double longitude;
    // 쿼리 from으로 준 구의 대표 좌표에서 농장까지의 직선 거리. from이 없으면 null
    private Double distanceKm;

    public static FarmItemDto of(Farm farm, Double distanceKm) {
        return FarmItemDto.builder()
                .farmId(IdPrefix.FARM.format(farm.getId()))
                .name(farm.getName())
                .district(farm.getDistrict())
                .address(farm.getAddress())
                .latitude(farm.getLatitude())
                .longitude(farm.getLongitude())
                .distanceKm(distanceKm)
                .build();
    }
}
