package community.likelion.growwithusserver.growth.weather;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.WeatherDay;
import java.time.LocalDate;
import java.util.List;
public interface GrowthWeatherReader { List<WeatherDay> read(int nx, int ny, LocalDate from, LocalDate to); }
