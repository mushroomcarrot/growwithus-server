package community.likelion.growwithusserver.growth.weather;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.WeatherDay;
import community.likelion.growwithusserver.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
@Component @RequiredArgsConstructor
public class StoredGrowthWeatherReader implements GrowthWeatherReader {
    private final WeatherService weather;
    public List<WeatherDay> read(int nx, int ny, LocalDate from, LocalDate to) {
        return weather.getDailyWeather(nx, ny, from, to).stream().map(d -> new WeatherDay(d.getDate(), d.getAvgTemp())).toList();
    }
}
