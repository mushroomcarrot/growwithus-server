package community.likelion.growwithusserver.weather.data.repository;

import community.likelion.growwithusserver.weather.data.entity.DailyWeather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyWeatherRepository extends JpaRepository<DailyWeather, Long> {
    Optional<DailyWeather> findByNxAndNyAndDate(int nx, int ny, LocalDate date);

    List<DailyWeather> findAllByNxAndNyAndDateBetweenOrderByDateAsc(int nx, int ny, LocalDate from, LocalDate to);
}
