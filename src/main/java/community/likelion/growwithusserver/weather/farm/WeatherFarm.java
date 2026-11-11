package community.likelion.growwithusserver.weather.farm;

import community.likelion.growwithusserver.common.id.IdPrefix;

/**
 * 날씨를 수집할 농장과 그 농장의 기상청 격자.
 *
 * @param farmId 농장 PK. 농장 도메인이 없어 설정의 기본 격자를 쓸 때는 null
 */
public record WeatherFarm(Long farmId, int nx, int ny) {

    // 응답과 로그에 쓰는 이름. 농장이 있으면 farm_3, 기본 격자면 grid_64_119
    public String label() {
        return farmId != null ? IdPrefix.FARM.format(farmId) : "grid_" + nx + "_" + ny;
    }
}
