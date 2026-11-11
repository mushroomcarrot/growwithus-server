package community.likelion.growwithusserver.demo;

import community.likelion.growwithusserver.common.redis.RedisService;
import community.likelion.growwithusserver.demo.data.dto.DemoStateResponseDto;
import community.likelion.growwithusserver.demo.data.dto.DemoWeatherRequestDto;
import community.likelion.growwithusserver.demo.data.dto.DemoWeatherResponseDto;
import community.likelion.growwithusserver.demo.data.exception.DemoWeatherNotAllowedException;
import community.likelion.growwithusserver.demo.service.DemoService;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import community.likelion.growwithusserver.weather.service.DemoWeatherStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoServiceTest {
    private final Map<String, String> redis = new HashMap<>();
    private DemoWeatherStore demoWeatherStore;
    private DemoService demoService;

    // Redis 대신 메모리에 담는 가짜 구현
    private static class FakeRedisService extends RedisService {
        private final Map<String, String> values;

        FakeRedisService(Map<String, String> values) {
            super(new org.springframework.data.redis.core.RedisTemplate<>());
            this.values = values;
        }

        @Override
        public void setValues(String key, String data) {
            values.put(key, data);
        }

        @Override
        public void setValues(String key, String data, Duration duration) {
            values.put(key, data);
        }

        @Override
        public Object getValues(String key) {
            return values.get(key);
        }

        @Override
        public void deleteValues(String key) {
            values.remove(key);
        }
    }

    @BeforeEach
    void setUp() {
        demoWeatherStore = new DemoWeatherStore(new FakeRedisService(redis), true);
        demoService = new DemoService(demoWeatherStore);
    }

    @Test
    void 날씨를_바꾸면_덮어쓰기가_저장되고_애니메이션을_돌려준다() {
        DemoWeatherResponseDto response = demoService.changeWeather(new DemoWeatherRequestDto(WeatherCondition.RAIN)).getData();

        assertThat(response.getCondition()).isEqualTo(WeatherCondition.RAIN);
        assertThat(response.getAnimation()).isEqualTo("rain_umbrella");
        assertThat(demoWeatherStore.get().orElseThrow().getCondition()).isEqualTo(WeatherCondition.RAIN);
        assertThat(demoService.getState().getData().getWeather()).isEqualTo(WeatherCondition.RAIN);
    }

    @Test
    void 시연에서_쓰지_않는_날씨는_C002이고_저장하지_않는다() {
        assertThatThrownBy(() -> demoService.changeWeather(new DemoWeatherRequestDto(WeatherCondition.COLD)))
                .isInstanceOf(DemoWeatherNotAllowedException.class);
        assertThat(redis).isEmpty();
    }

    @Test
    void 덮어쓴_적이_없으면_날씨는_null이다() {
        assertThat(demoService.getState().getData().getWeather()).isNull();
    }

    @Test
    void 초기화하면_맑음으로_돌아간다() {
        demoService.changeWeather(new DemoWeatherRequestDto(WeatherCondition.HOT));

        DemoStateResponseDto response = demoService.reset().getData();

        assertThat(response.getWeather()).isEqualTo(WeatherCondition.SUNNY);
        assertThat(demoWeatherStore.get().orElseThrow().getCondition()).isEqualTo(WeatherCondition.SUNNY);
    }
}
