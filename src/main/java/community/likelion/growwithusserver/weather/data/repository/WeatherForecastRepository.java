package community.likelion.growwithusserver.weather.data.repository;

import community.likelion.growwithusserver.weather.data.entity.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {
    // 기준 시각 이전에서 가장 가까운 값 (현재 날씨)
    Optional<WeatherForecast> findFirstByNxAndNyAndForecastAtLessThanEqualOrderByForecastAtDesc(int nx, int ny, LocalDateTime at);

    // 기준 시각 이후에서 가장 가까운 값 (지난 값이 하나도 없을 때)
    Optional<WeatherForecast> findFirstByNxAndNyAndForecastAtGreaterThanOrderByForecastAtAsc(int nx, int ny, LocalDateTime at);

    List<WeatherForecast> findAllByNxAndNyAndForecastAtBetweenOrderByForecastAtAsc(int nx, int ny, LocalDateTime from, LocalDateTime to);
}
