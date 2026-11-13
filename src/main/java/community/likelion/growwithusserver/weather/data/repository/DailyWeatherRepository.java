package community.likelion.growwithusserver.weather.data.repository;

import community.likelion.growwithusserver.weather.data.entity.DailyWeather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyWeatherRepository extends JpaRepository<DailyWeather, Long> {
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "insert into daily_weather (nx, ny, weather_date, avg_temp, min_temp, max_temp, source_url, observed, updated_at) "
            + "values (:nx, :ny, :date, :avg, :min, :max, :source, true, :at) on duplicate key update "
            + "avg_temp=values(avg_temp), min_temp=values(min_temp), max_temp=values(max_temp), source_url=values(source_url), observed=true, updated_at=values(updated_at)", nativeQuery = true)
    int upsertObservation(@org.springframework.data.repository.query.Param("nx") int nx,
            @org.springframework.data.repository.query.Param("ny") int ny, @org.springframework.data.repository.query.Param("date") LocalDate date,
            @org.springframework.data.repository.query.Param("avg") double avg, @org.springframework.data.repository.query.Param("min") double min,
            @org.springframework.data.repository.query.Param("max") double max, @org.springframework.data.repository.query.Param("source") String source,
            @org.springframework.data.repository.query.Param("at") java.time.LocalDateTime at);
    Optional<DailyWeather> findByNxAndNyAndDate(int nx, int ny, LocalDate date);

    List<DailyWeather> findAllByNxAndNyAndDateBetweenOrderByDateAsc(int nx, int ny, LocalDate from, LocalDate to);
}
