package community.likelion.growwithusserver.weather.client;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 지금 시각에 받을 수 있는 가장 최근 발표시각을 고른다. 아직 발표되지 않은 시각을 요청하면 NODATA_ERROR가 난다.
 *
 * 1. 초단기실황: 매시 정각 발표, 10분 이후 제공
 * 2. 단기예보: 02, 05, 08, 11, 14, 17, 20, 23시 발표, 각 10분 이후 제공
 */
public final class KmaBaseTime {
    private static final int AVAILABLE_AFTER_MINUTES = 10;
    private static final int FORECAST_FIRST_HOUR = 2;
    private static final int FORECAST_INTERVAL_HOURS = 3;

    private KmaBaseTime() {
    }

    // 초단기실황의 발표시각
    public static LocalDateTime observation(LocalDateTime now) {
        return now.minusMinutes(AVAILABLE_AFTER_MINUTES).truncatedTo(ChronoUnit.HOURS);
    }

    // 단기예보의 발표시각
    public static LocalDateTime forecast(LocalDateTime now) {
        LocalDateTime available = now.minusMinutes(AVAILABLE_AFTER_MINUTES).truncatedTo(ChronoUnit.HOURS);
        // 02시를 기준으로 3시간 간격에 맞춰 내린다. 02시 전이면 전날 23시가 된다
        int hoursPastRelease = Math.floorMod(available.getHour() - FORECAST_FIRST_HOUR, FORECAST_INTERVAL_HOURS);

        return available.minusHours(hoursPastRelease);
    }
}
