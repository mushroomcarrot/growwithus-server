package community.likelion.growwithusserver.weather;

import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import community.likelion.growwithusserver.weather.service.WeatherConditionResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherConditionResolverTest {

    @Test
    void 비가_오면_기온과_상관없이_RAIN이다() {
        assertThat(WeatherConditionResolver.resolve(31.0, 1, 4)).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditionResolver.resolve(12.0, 4, 3)).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditionResolver.resolve(3.0, 2, 4)).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditionResolver.resolve(12.0, 5, null)).isEqualTo(WeatherCondition.RAIN);
    }

    @Test
    void 눈이_오면_COLD다() {
        assertThat(WeatherConditionResolver.resolve(-1.0, 3, 4)).isEqualTo(WeatherCondition.COLD);
        assertThat(WeatherConditionResolver.resolve(0.5, 7, null)).isEqualTo(WeatherCondition.COLD);
    }

    @Test
    void 기온_30도_이상은_HOT_5도_이하는_COLD다() {
        assertThat(WeatherConditionResolver.resolve(30.0, 0, 1)).isEqualTo(WeatherCondition.HOT);
        assertThat(WeatherConditionResolver.resolve(29.9, 0, 1)).isEqualTo(WeatherCondition.SUNNY);
        assertThat(WeatherConditionResolver.resolve(5.0, 0, 1)).isEqualTo(WeatherCondition.COLD);
        assertThat(WeatherConditionResolver.resolve(5.1, 0, 4)).isEqualTo(WeatherCondition.CLOUDY);
    }

    @Test
    void 하늘상태로_SUNNY와_CLOUDY를_나눈다() {
        assertThat(WeatherConditionResolver.resolve(14.2, 0, 1)).isEqualTo(WeatherCondition.SUNNY);
        assertThat(WeatherConditionResolver.resolve(14.2, 0, 3)).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(WeatherConditionResolver.resolve(14.2, 0, 4)).isEqualTo(WeatherCondition.CLOUDY);
    }

    @Test
    void 값이_없으면_SUNNY다() {
        assertThat(WeatherConditionResolver.resolve(null, null, null)).isEqualTo(WeatherCondition.SUNNY);
        assertThat(WeatherConditionResolver.resolve(14.2, null, null)).isEqualTo(WeatherCondition.SUNNY);
    }
}
