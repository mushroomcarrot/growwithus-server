package community.likelion.growwithusserver.farm.service;

import community.likelion.growwithusserver.farm.data.enums.District;

import java.util.Map;
import java.util.Optional;

/**
 * 용인시 읍면동의 구와 대표 좌표. 주말농장 원본에 좌표가 없어서 주소의 읍면동 대표 좌표를 농장 위치로 쓴다.
 * 좌표 출처는 기상청 격자 엑셀이다 (docs/public-data.md). 표에 없는 기흥구, 수지구의 동은 구 대표 좌표를 쓴다.
 */
public record YonginTown(District district, double latitude, double longitude) {
    private static final Map<String, YonginTown> TOWNS = Map.ofEntries(
            Map.entry("포곡읍", new YonginTown(District.CHEOIN, 37.2751, 127.2329)),
            Map.entry("모현읍", new YonginTown(District.CHEOIN, 37.3267, 127.2446)),
            Map.entry("이동읍", new YonginTown(District.CHEOIN, 37.1382, 127.1983)),
            // 2017년에 이동읍으로 승격했다. 원본(2015.12)은 이동면으로 적혀 있다
            Map.entry("이동면", new YonginTown(District.CHEOIN, 37.1382, 127.1983)),
            Map.entry("남사읍", new YonginTown(District.CHEOIN, 37.1129, 127.1527)),
            Map.entry("남사면", new YonginTown(District.CHEOIN, 37.1129, 127.1527)),
            Map.entry("양지읍", new YonginTown(District.CHEOIN, 37.2355, 127.2799)),
            Map.entry("양지면", new YonginTown(District.CHEOIN, 37.2355, 127.2799)),
            Map.entry("원삼면", new YonginTown(District.CHEOIN, 37.1635, 127.3151)),
            Map.entry("백암면", new YonginTown(District.CHEOIN, 37.1605, 127.3776)),
            Map.entry("역북동", new YonginTown(District.CHEOIN, 37.2346, 127.1881)),
            Map.entry("삼가동", new YonginTown(District.CHEOIN, 37.2494, 127.1659)),
            Map.entry("보정동", new YonginTown(District.GIHEUNG, District.GIHEUNG.getLatitude(), District.GIHEUNG.getLongitude()))
    );

    // 주소의 첫 단어(읍면동)로 찾는다. 예: "원삼면 학일리 331-1번지 일원"
    public static Optional<YonginTown> find(String location) {
        if (location == null || location.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TOWNS.get(location.trim().split("\\s+")[0]));
    }
}
