package community.likelion.growwithusserver.weather.service;

import community.likelion.growwithusserver.common.time.DateTimes;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import community.likelion.growwithusserver.weather.data.entity.DailyWeather;
import community.likelion.growwithusserver.weather.data.entity.WeatherForecast;
import community.likelion.growwithusserver.weather.data.repository.DailyWeatherRepository;
import community.likelion.growwithusserver.weather.data.repository.WeatherForecastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// 날씨 조회와 저장의 단일 창구. 홈, 작업 생성, 성장 시뮬레이션은 저장소를 직접 보지 않고 여기를 쓴다.
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {
    private final WeatherForecastRepository weatherForecastRepository;
    private final DailyWeatherRepository dailyWeatherRepository;
    private final DemoWeatherStore demoWeatherStore;

    @Transactional(readOnly = true)
    public boolean isRainExpected(int nx, int ny, LocalDate date) {
        var demo = demoWeatherStore.get();
        if (demo.isPresent()) return demo.get().getCondition() == community.likelion.growwithusserver.weather.data.enums.WeatherCondition.RAIN;
        var current = getCurrentWeather(nx, ny);
        if (current.isPresent() && (current.get().getCondition() == community.likelion.growwithusserver.weather.data.enums.WeatherCondition.RAIN
                || (current.get().getRainProbability() != null && current.get().getRainProbability() >= 60))) return true;
        return getForecasts(nx, ny, date.atStartOfDay(), date.atTime(LocalTime.MAX)).stream()
                .anyMatch(f -> (f.getPrecipitationType() != null && f.getPrecipitationType() > 0)
                        || (f.getRainProbability() != null && f.getRainProbability() >= 60));
    }

    /**
     * 격자의 현재 날씨. 시연 날씨가 설정되어 있으면 그 값이 저장된 날씨보다 우선한다.
     * 수집된 값이 하나도 없으면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentWeatherDto> getCurrentWeather(int nx, int ny) {
        Optional<CurrentWeatherDto> demoWeather = demoWeatherStore.get();
        if (demoWeather.isPresent()) {
            return demoWeather;
        }

        LocalDateTime now = LocalDateTime.now();
        return weatherForecastRepository
                .findFirstByNxAndNyAndForecastAtLessThanEqualOrderByForecastAtDesc(nx, ny, now)
                .or(() -> weatherForecastRepository.findFirstByNxAndNyAndForecastAtGreaterThanOrderByForecastAtAsc(nx, ny, now))
                .map(this::toCurrentWeather);
    }

    // 격자의 시간대별 예보 (작업 생성에서 "오후에 비" 같은 판단에 쓴다). 시연 날씨는 반영하지 않는다.
    @Transactional(readOnly = true)
    public List<WeatherForecast> getForecasts(int nx, int ny, LocalDateTime from, LocalDateTime to) {
        return weatherForecastRepository.findAllByNxAndNyAndForecastAtBetweenOrderByForecastAtAsc(nx, ny, from, to);
    }

    // 격자의 일별 기온 집계 (적산온도 계산용), 날짜 오름차순
    @Transactional(readOnly = true)
    public List<DailyWeather> getDailyWeather(int nx, int ny, LocalDate from, LocalDate to) {
        return dailyWeatherRepository.findAllByNxAndNyAndDateBetweenOrderByDateAsc(nx, ny, from, to);
    }

    /**
     * 수집한 날씨를 저장하고, 값이 바뀐 날짜의 일별 집계를 다시 계산한다.
     * 격자와 시각이 같은 줄이 있으면 덮어써서 중복으로 쌓이지 않는다. 실황이 들어온 줄은 예보로 덮어쓰지 않는다.
     *
     * @return 새로 만들거나 갱신한 줄 수
     */
    @Transactional
    public int saveForecasts(int nx, int ny, List<WeatherForecastData> forecasts) {
        if (forecasts.isEmpty()) {
            return 0;
        }

        LocalDateTime from = forecasts.stream().map(WeatherForecastData::forecastAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime to = forecasts.stream().map(WeatherForecastData::forecastAt).max(LocalDateTime::compareTo).orElseThrow();
        Map<LocalDateTime, WeatherForecast> existing = weatherForecastRepository
                .findAllByNxAndNyAndForecastAtBetweenOrderByForecastAtAsc(nx, ny, from, to).stream()
                .collect(Collectors.toMap(WeatherForecast::getForecastAt, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        int saved = 0;
        for (WeatherForecastData data : forecasts) {
            WeatherForecast forecast = existing.get(data.forecastAt());
            if (forecast == null) {
                forecast = WeatherForecast.builder().nx(nx).ny(ny).forecastAt(data.forecastAt()).build();
                existing.put(data.forecastAt(), forecast);
            } else if (forecast.isObserved() && !data.observed()) {
                continue;
            }

            // 실황에는 강수확률과 하늘상태가 없으므로, 값이 없으면 앞서 받은 예보 값을 남긴다
            forecast.setTemperature(data.temperature() != null ? data.temperature() : forecast.getTemperature());
            forecast.setRainProbability(data.rainProbability() != null ? data.rainProbability() : forecast.getRainProbability());
            forecast.setPrecipitationType(data.precipitationType() != null ? data.precipitationType() : forecast.getPrecipitationType());
            forecast.setSkyStatus(data.skyStatus() != null ? data.skyStatus() : forecast.getSkyStatus());
            forecast.setObserved(data.observed());
            forecast.setUpdatedAt(now);
            weatherForecastRepository.save(forecast);
            saved++;
        }

        forecasts.stream()
                .map(data -> data.forecastAt().toLocalDate())
                .distinct()
                .forEach(date -> aggregateDaily(nx, ny, date));
        log.info("[saveForecasts] 날씨 저장: 격자 ({}, {}), {}건", nx, ny, saved);

        return saved;
    }

    /**
     * 하루치 기온으로 평균, 최고, 최저를 다시 계산해 저장한다. 그날 기온이 하나도 없으면 비어 있다.
     * 지난 시각은 실황, 남은 시각은 예보가 섞여 있어서 수집할 때마다 실제 값에 가까워진다.
     */
    @Transactional
    public Optional<DailyWeather> aggregateDaily(int nx, int ny, LocalDate date) {
        Optional<DailyWeather> existing = dailyWeatherRepository.findByNxAndNyAndDate(nx, ny, date);
        if (existing.filter(DailyWeather::isObserved).isPresent()) return existing;
        DoubleSummaryStatistics statistics = weatherForecastRepository
                .findAllByNxAndNyAndForecastAtBetweenOrderByForecastAtAsc(nx, ny, date.atStartOfDay(), date.atTime(LocalTime.MAX)).stream()
                .map(WeatherForecast::getTemperature)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        if (statistics.getCount() == 0) {
            return Optional.empty();
        }

        DailyWeather dailyWeather = existing
                .orElseGet(() -> DailyWeather.builder().nx(nx).ny(ny).date(date).build());
        dailyWeather.setAvgTemp(Math.round(statistics.getAverage() * 10) / 10.0);
        dailyWeather.setMaxTemp(statistics.getMax());
        dailyWeather.setMinTemp(statistics.getMin());
        dailyWeather.setUpdatedAt(LocalDateTime.now());

        return Optional.of(dailyWeatherRepository.save(dailyWeather));
    }

    private CurrentWeatherDto toCurrentWeather(WeatherForecast forecast) {
        return CurrentWeatherDto.builder()
                .condition(WeatherConditionResolver.resolve(
                        forecast.getTemperature(), forecast.getPrecipitationType(), forecast.getSkyStatus()))
                .temperature(forecast.getTemperature())
                .rainProbability(forecast.getRainProbability())
                .updatedAt(DateTimes.toOffset(forecast.getUpdatedAt()))
                .build();
    }
}
