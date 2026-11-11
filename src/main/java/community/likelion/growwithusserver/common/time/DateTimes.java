package community.likelion.growwithusserver.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

// 응답 시각은 OffsetDateTime으로 내려준다 (ISO 8601, +09:00). 엔티티의 LocalDateTime은 여기서 바꾼다.
public final class DateTimes {
    private DateTimes() {
    }

    // JVM 기본 시간대(Asia/Seoul) 기준 현재 시각, 초 단위
    public static OffsetDateTime now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public static OffsetDateTime toOffset(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
