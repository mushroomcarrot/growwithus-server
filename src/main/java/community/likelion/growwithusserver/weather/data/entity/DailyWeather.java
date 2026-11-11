package community.likelion.growwithusserver.weather.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 격자별 하루 기온 집계. 성장 시뮬레이션의 적산온도 계산에 쓴다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "daily_weather",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_weather_grid_date", columnNames = {"nx", "ny", "weather_date"}))
public class DailyWeather {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int nx;

    @Column(nullable = false)
    private int ny;

    @Column(name = "weather_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private double avgTemp;

    @Column(nullable = false)
    private double maxTemp;

    @Column(nullable = false)
    private double minTemp;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
