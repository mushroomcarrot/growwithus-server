package community.likelion.growwithusserver.weather.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 격자와 시각마다 한 줄. 같은 시각의 예보가 다시 오거나 실황이 들어오면 새로 쌓지 않고 덮어쓴다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weather_forecast",
        uniqueConstraints = @UniqueConstraint(name = "uk_weather_forecast_grid_time", columnNames = {"nx", "ny", "forecast_at"}))
public class WeatherForecast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기상청 격자 좌표 (농장 위치)
    @Column(nullable = false)
    private int nx;

    @Column(nullable = false)
    private int ny;

    // 예보 또는 관측 대상 시각 (정시)
    @Column(name = "forecast_at", nullable = false)
    private LocalDateTime forecastAt;

    // 기온 (℃). 기상청 TMP, T1H
    @Column
    private Double temperature;

    // 강수확률 (%). 기상청 POP. 실황에는 없다
    @Column
    private Integer rainProbability;

    // 강수형태 코드. 기상청 PTY: 0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기, 5 빗방울, 6 빗방울눈날림, 7 눈날림
    @Column
    private Integer precipitationType;

    // 하늘상태 코드. 기상청 SKY: 1 맑음, 3 구름많음, 4 흐림. 실황에는 없다
    @Column
    private Integer skyStatus;

    // true면 초단기실황(관측값), false면 단기예보. 실황이 들어온 줄은 예보로 다시 덮어쓰지 않는다
    @Builder.Default
    @Column(nullable = false)
    private boolean observed = false;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
