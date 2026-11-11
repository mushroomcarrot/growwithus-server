package community.likelion.growwithusserver.weather;

import community.likelion.growwithusserver.common.redis.RedisService;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import community.likelion.growwithusserver.weather.data.entity.DailyWeather;
import community.likelion.growwithusserver.weather.data.entity.WeatherForecast;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import community.likelion.growwithusserver.weather.data.repository.DailyWeatherRepository;
import community.likelion.growwithusserver.weather.data.repository.WeatherForecastRepository;
import community.likelion.growwithusserver.weather.service.DemoWeatherStore;
import community.likelion.growwithusserver.weather.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {
    private static final int NX = 64;
    private static final int NY = 119;

    @Mock
    private WeatherForecastRepository weatherForecastRepository;
    @Mock
    private DailyWeatherRepository dailyWeatherRepository;
    @Mock
    private RedisService redisService;

    private WeatherService service(boolean demoEnabled) {
        return new WeatherService(weatherForecastRepository, dailyWeatherRepository,
                new DemoWeatherStore(redisService, demoEnabled));
    }

    private WeatherForecast forecast(LocalDateTime at, Double temperature, Integer pop, Integer pty, Integer sky, boolean observed) {
        return WeatherForecast.builder()
                .nx(NX).ny(NY).forecastAt(at)
                .temperature(temperature).rainProbability(pop).precipitationType(pty).skyStatus(sky)
                .observed(observed)
                .updatedAt(LocalDateTime.of(2026, 11, 10, 9, 0))
                .build();
    }

    @Test
    void 시연_날씨가_있으면_저장된_날씨보다_우선한다() {
        when(redisService.getValues("demo:weather")).thenReturn("RAIN|2026-11-10T09:30:00+09:00");

        CurrentWeatherDto weather = service(true).getCurrentWeather(NX, NY).orElseThrow();

        assertThat(weather.getCondition()).isEqualTo(WeatherCondition.RAIN);
        assertThat(weather.getRainProbability()).isEqualTo(90);
        assertThat(weather.getUpdatedAt()).hasToString("2026-11-10T09:30+09:00");
        verify(weatherForecastRepository, never())
                .findFirstByNxAndNyAndForecastAtLessThanEqualOrderByForecastAtDesc(anyInt(), anyInt(), any());
    }

    @Test
    void 시연이_꺼져_있으면_남아_있는_시연_날씨를_무시한다() {
        lenient().when(redisService.getValues("demo:weather")).thenReturn("RAIN|2026-11-10T09:30:00+09:00");
        when(weatherForecastRepository.findFirstByNxAndNyAndForecastAtLessThanEqualOrderByForecastAtDesc(eq(NX), eq(NY), any()))
                .thenReturn(Optional.of(forecast(LocalDateTime.of(2026, 11, 10, 9, 0), 14.2, 10, 0, 1, true)));

        CurrentWeatherDto weather = service(false).getCurrentWeather(NX, NY).orElseThrow();

        assertThat(weather.getCondition()).isEqualTo(WeatherCondition.SUNNY);
        assertThat(weather.getTemperature()).isEqualTo(14.2);
        assertThat(weather.getRainProbability()).isEqualTo(10);
        assertThat(weather.getUpdatedAt()).hasToString("2026-11-10T09:00+09:00");
    }

    @Test
    void 지난_값이_없으면_가장_가까운_예보를_쓰고_아무것도_없으면_비어_있다() {
        when(weatherForecastRepository.findFirstByNxAndNyAndForecastAtLessThanEqualOrderByForecastAtDesc(eq(NX), eq(NY), any()))
                .thenReturn(Optional.empty());
        when(weatherForecastRepository.findFirstByNxAndNyAndForecastAtGreaterThanOrderByForecastAtAsc(eq(NX), eq(NY), any()))
                .thenReturn(Optional.of(forecast(LocalDateTime.of(2026, 11, 10, 12, 0), 31.5, 0, 0, 1, false)))
                .thenReturn(Optional.empty());

        WeatherService service = service(true);

        assertThat(service.getCurrentWeather(NX, NY).orElseThrow().getCondition()).isEqualTo(WeatherCondition.HOT);
        assertThat(service.getCurrentWeather(NX, NY)).isEmpty();
    }

    @Test
    void 같은_시각은_덮어쓰고_실황은_예보로_덮어쓰지_않는다() {
        LocalDateTime nine = LocalDateTime.of(2026, 11, 10, 9, 0);
        LocalDateTime ten = LocalDateTime.of(2026, 11, 10, 10, 0);
        LocalDateTime eleven = LocalDateTime.of(2026, 11, 10, 11, 0);
        WeatherForecast observedNine = forecast(nine, 13.0, 20, 0, 1, true);
        WeatherForecast forecastTen = forecast(ten, 14.0, 20, 0, 1, false);
        List<WeatherForecast> stored = new ArrayList<>(List.of(observedNine, forecastTen));
        when(weatherForecastRepository.findAllByNxAndNyAndForecastAtBetweenOrderByForecastAtAsc(eq(NX), eq(NY), any(), any()))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(weatherForecastRepository.save(any(WeatherForecast.class))).thenAnswer(invocation -> {
            WeatherForecast saved = invocation.getArgument(0);
            if (!stored.contains(saved)) {
                stored.add(saved);
            }
            return saved;
        });
        when(dailyWeatherRepository.findByNxAndNyAndDate(NX, NY, LocalDate.of(2026, 11, 10))).thenReturn(Optional.empty());
        when(dailyWeatherRepository.save(any(DailyWeather.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int saved = service(true).saveForecasts(NX, NY, List.of(
                // 실황이 있는 9시의 예보: 건너뜀
                new WeatherForecastData(nine, 99.0, 80, 1, 4, false),
                // 10시 실황: 기온만 바뀌고 강수확률, 하늘상태는 예보 값을 남김
                new WeatherForecastData(ten, 15.0, null, 0, null, true),
                // 11시 예보: 새로 추가
                new WeatherForecastData(eleven, 17.0, 30, 0, 3, false)
        ));

        assertThat(saved).isEqualTo(2);
        assertThat(stored).hasSize(3);
        assertThat(observedNine.getTemperature()).isEqualTo(13.0);
        assertThat(forecastTen.getTemperature()).isEqualTo(15.0);
        assertThat(forecastTen.getRainProbability()).isEqualTo(20);
        assertThat(forecastTen.getSkyStatus()).isEqualTo(1);
        assertThat(forecastTen.isObserved()).isTrue();

        ArgumentCaptor<DailyWeather> daily = ArgumentCaptor.forClass(DailyWeather.class);
        verify(dailyWeatherRepository).save(daily.capture());
        assertThat(daily.getValue().getDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(daily.getValue().getAvgTemp()).isEqualTo(15.0);
        assertThat(daily.getValue().getMaxTemp()).isEqualTo(17.0);
        assertThat(daily.getValue().getMinTemp()).isEqualTo(13.0);
    }

    @Test
    void 기온이_없는_날은_집계하지_않는다() {
        when(weatherForecastRepository.findAllByNxAndNyAndForecastAtBetweenOrderByForecastAtAsc(eq(NX), eq(NY), any(), any()))
                .thenReturn(List.of());

        assertThat(service(true).aggregateDaily(NX, NY, LocalDate.of(2026, 11, 10))).isEmpty();
        verify(dailyWeatherRepository, never()).save(any());
    }
}
