package community.likelion.growwithusserver.weather;
import community.likelion.growwithusserver.weather.service.*;
import community.likelion.growwithusserver.weather.service.DailyWeatherImportService.*;
import community.likelion.growwithusserver.weather.data.entity.DailyWeather;
import community.likelion.growwithusserver.weather.data.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DailyWeatherImportTest {
    @Test void rejectsIncompleteDatesNonFiniteTemperaturesAndDuplicateDays() {
        var yesterday = LocalDate.now().minusDays(1);
        assertThrows(IllegalArgumentException.class, () -> new Day(LocalDate.now(), 10, 5, 15));
        assertThrows(IllegalArgumentException.class, () -> new Day(yesterday, Double.NaN, 5, 15));
        assertThrows(IllegalArgumentException.class, () -> new Day(yesterday, 10, 11, 15));
        var valid = new Day(yesterday, 10, 5, 15);
        assertThrows(IllegalArgumentException.class, () -> new ImportRequest(64,119,"https://example.invalid/source",List.of(valid,valid)));
        assertThrows(IllegalArgumentException.class, () -> new ImportRequest(64,119,"file:///private",List.of(valid)));
    }
    @Test void validatedHistoryIsWrittenWithItsSourceAndGrid() {
        var days = mock(DailyWeatherRepository.class); var date = LocalDate.now().minusDays(1);
        var service = new DailyWeatherImportService(days);
        assertEquals(1, service.importDays(new ImportRequest(64,119,"https://example.invalid/source",List.of(new Day(date,10,5,15)))).importedDays());
        verify(days).upsertObservation(eq(64),eq(119),eq(date),eq(10d),eq(5d),eq(15d),eq("https://example.invalid/source"),any());
    }
    @Test void forecastAggregationNeverReplacesAnImportedObservation() {
        var days = mock(DailyWeatherRepository.class); var forecasts = mock(WeatherForecastRepository.class);
        var date = LocalDate.now().minusDays(1);
        var observation = DailyWeather.builder().date(date).avgTemp(10).minTemp(5).maxTemp(15).observed(true).build();
        when(days.findByNxAndNyAndDate(64,119,date)).thenReturn(Optional.of(observation));
        var result = new WeatherService(forecasts, days, mock(DemoWeatherStore.class)).aggregateDaily(64,119,date);
        assertSame(observation, result.orElseThrow()); verifyNoInteractions(forecasts); verify(days,never()).save(any());
    }
}
