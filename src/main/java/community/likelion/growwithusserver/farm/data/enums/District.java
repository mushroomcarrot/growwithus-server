package community.likelion.growwithusserver.farm.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 용인시의 구 (명세의 district). 좌표는 기상청 격자 엑셀의 구 대표 좌표다 (docs/public-data.md).
@Getter
@RequiredArgsConstructor
public enum District {
    SUJI("수지구", 37.3194, 127.1001),
    GIHEUNG("기흥구", 37.2776, 127.1168),
    CHEOIN("처인구", 37.2315, 127.2038);

    private final String koreanName;
    private final double latitude;
    private final double longitude;
}
