package community.likelion.growwithusserver.common.time;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// application.yml의 spring.jackson 설정으로 날짜는 YYYY-MM-DD, 시각은 ISO 8601 +09:00으로 나가는지 확인한다
@JsonTest
class DateTimeSerializationTest {
    record Sample(LocalDate date, OffsetDateTime at) {
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 날짜와_시각_형식() {
        Sample sample = new Sample(LocalDate.of(2026, 11, 10),
                OffsetDateTime.of(2026, 11, 10, 9, 30, 0, 0, ZoneOffset.ofHours(9)));

        assertThat(objectMapper.writeValueAsString(sample))
                .isEqualTo("{\"date\":\"2026-11-10\",\"at\":\"2026-11-10T09:30:00+09:00\"}");
    }

    @Test
    void 다른_오프셋의_시각도_한국_시간으로_내려준다() {
        Sample sample = new Sample(null, OffsetDateTime.of(2026, 11, 10, 0, 30, 0, 0, ZoneOffset.UTC));

        assertThat(objectMapper.writeValueAsString(sample)).contains("\"at\":\"2026-11-10T09:30:00+09:00\"");
    }

    @Test
    void 요청의_시각은_한국_시간으로_읽는다() {
        Sample sample = objectMapper.readValue("{\"date\":\"2026-11-10\",\"at\":\"2026-11-10T00:30:00Z\"}", Sample.class);

        assertThat(sample.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(sample.at()).isEqualTo(OffsetDateTime.of(2026, 11, 10, 9, 30, 0, 0, ZoneOffset.ofHours(9)));
        assertThat(sample.at().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    void LocalDateTime은_DateTimes로_오프셋을_붙인다() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        try {
            OffsetDateTime at = DateTimes.toOffset(LocalDateTime.of(2026, 11, 10, 9, 30, 0, 123_000_000));
            assertThat(at).hasToString("2026-11-10T09:30+09:00");
            assertThat(DateTimes.toOffset(null)).isNull();
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }
}
