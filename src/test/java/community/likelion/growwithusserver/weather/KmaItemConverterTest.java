package community.likelion.growwithusserver.weather;

import community.likelion.growwithusserver.weather.client.KmaItemConverter;
import community.likelion.growwithusserver.weather.client.KmaResponse;
import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmaItemConverterTest {

    private KmaResponse.Item observed(String category, String value) {
        return new KmaResponse.Item("20261111", "1100", category, value, null, null, null);
    }

    private KmaResponse.Item forecast(String time, String category, String value) {
        return new KmaResponse.Item("20261111", "0500", category, null, "20261111", time, value);
    }

    @Test
    void 실황은_발표시각_한_건으로_묶고_강수확률과_하늘상태는_비운다() {
        List<WeatherForecastData> result = KmaItemConverter.toObservation(List.of(
                observed("PTY", "0"), observed("REH", "69"), observed("RN1", "0"), observed("T1H", "9.2")));

        assertThat(result).containsExactly(
                new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 11, 0), 9.2, null, 0, null, true));
    }

    @Test
    void 예보는_예보시각마다_한_건으로_묶는다() {
        List<WeatherForecastData> result = KmaItemConverter.toForecasts(List.of(
                forecast("0600", "TMP", "2"), forecast("0600", "SKY", "1"), forecast("0600", "PTY", "0"),
                forecast("0600", "POP", "0"), forecast("0600", "PCP", "강수없음"),
                forecast("0700", "TMP", "3"), forecast("0700", "SKY", "4"), forecast("0700", "PTY", "1"),
                forecast("0700", "POP", "60")));

        assertThat(result).containsExactly(
                new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 6, 0), 2.0, 0, 0, 1, false),
                new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 7, 0), 3.0, 60, 1, 4, false));
    }

    @Test
    void 결측값과_숫자가_아닌_값은_버린다() {
        List<WeatherForecastData> result = KmaItemConverter.toForecasts(List.of(
                forecast("0600", "TMP", "-999"), forecast("0600", "POP", "30"),
                forecast("0700", "TMP", "-998.9"), forecast("0700", "PCP", "1mm 미만")));

        assertThat(result).containsExactly(
                new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 6, 0), null, 30, null, null, false));
    }

    @Test
    void 최저_최고기온_같은_다른_요소만_있는_시각은_만들지_않는다() {
        assertThat(KmaItemConverter.toForecasts(List.of(forecast("1500", "TMX", "12.0")))).isEmpty();
    }
}
