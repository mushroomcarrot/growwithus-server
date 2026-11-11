package community.likelion.growwithusserver.weather;

import community.likelion.growwithusserver.weather.client.KmaBaseTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaBaseTimeTest {

    @Test
    void 실황은_10분이_지나야_이번_정각을_쓴다() {
        assertThat(KmaBaseTime.observation(LocalDateTime.of(2026, 11, 11, 11, 9)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 10, 0));
        assertThat(KmaBaseTime.observation(LocalDateTime.of(2026, 11, 11, 11, 10)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 11, 0));
    }

    @Test
    void 실황은_자정_직후에_전날_23시를_쓴다() {
        assertThat(KmaBaseTime.observation(LocalDateTime.of(2026, 11, 11, 0, 5)))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 23, 0));
    }

    @Test
    void 단기예보는_가장_최근_발표시각을_쓴다() {
        assertThat(KmaBaseTime.forecast(LocalDateTime.of(2026, 11, 11, 5, 9)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 2, 0));
        assertThat(KmaBaseTime.forecast(LocalDateTime.of(2026, 11, 11, 5, 10)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 5, 0));
        assertThat(KmaBaseTime.forecast(LocalDateTime.of(2026, 11, 11, 13, 15)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 11, 0));
        assertThat(KmaBaseTime.forecast(LocalDateTime.of(2026, 11, 11, 23, 59)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 23, 0));
    }

    @Test
    void 단기예보는_02시_10분_전이면_전날_23시를_쓴다() {
        assertThat(KmaBaseTime.forecast(LocalDateTime.of(2026, 11, 11, 2, 9)))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 23, 0));
        assertThat(KmaBaseTime.forecast(LocalDateTime.of(2026, 11, 11, 0, 30)))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 23, 0));
    }
}
