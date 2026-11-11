package community.likelion.growwithusserver.weather.service;

import community.likelion.growwithusserver.common.redis.RedisService;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

// 시연용 날씨 덮어쓰기 (PUT /demo/weather). Redis에 두고 WeatherService가 저장된 날씨보다 먼저 본다.
// 시연 텃밭이 하나라서 격자를 가리지 않고 모든 조회에 적용한다. demo.enabled=false면 값이 남아 있어도 무시한다.
@Slf4j
@Component
public class DemoWeatherStore {
    static final String KEY = "demo:weather";
    private static final String SEPARATOR = "|";

    private final RedisService redisService;
    private final boolean demoEnabled;

    public DemoWeatherStore(RedisService redisService, @Value("${demo.enabled}") boolean demoEnabled) {
        this.redisService = redisService;
        this.demoEnabled = demoEnabled;
    }

    public void set(WeatherCondition condition) {
        redisService.setValues(KEY, condition.name() + SEPARATOR + OffsetDateTime.now().withNano(0));
    }

    public void clear() {
        redisService.deleteValues(KEY);
    }

    public Optional<CurrentWeatherDto> get() {
        if (!demoEnabled) {
            return Optional.empty();
        }

        Object value = redisService.getValues(KEY);
        if (value == null) {
            return Optional.empty();
        }

        try {
            String[] parts = value.toString().split("\\" + SEPARATOR, 2);
            return Optional.of(preset(WeatherCondition.valueOf(parts[0]), OffsetDateTime.parse(parts[1])));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | DateTimeParseException e) {
            log.warn("[get] 시연 날씨 값을 읽을 수 없어 무시: {}", value);
            return Optional.empty();
        }
    }

    // 덮어쓴 상태에 어울리는 기온과 강수확률 (시연용 고정값)
    private CurrentWeatherDto preset(WeatherCondition condition, OffsetDateTime updatedAt) {
        double temperature = switch (condition) {
            case HOT -> 31.0;
            case COLD -> 2.0;
            case RAIN -> 12.0;
            case SUNNY, CLOUDY -> 14.2;
        };
        int rainProbability = switch (condition) {
            case RAIN -> 90;
            case CLOUDY -> 30;
            case SUNNY, HOT, COLD -> 10;
        };

        return CurrentWeatherDto.builder()
                .condition(condition)
                .temperature(temperature)
                .rainProbability(rainProbability)
                .updatedAt(updatedAt)
                .build();
    }
}
