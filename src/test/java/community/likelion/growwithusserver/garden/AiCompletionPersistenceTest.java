package community.likelion.growwithusserver.garden;
import community.likelion.growwithusserver.care.guidance.*;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.weather.data.repository.DailyWeatherRepository;
import community.likelion.growwithusserver.weather.service.*;
import community.likelion.growwithusserver.weather.service.DailyWeatherImportService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.profiles.active=local","demo.enabled=false","scheduler.enabled=false"})
@ActiveProfiles("local") @Transactional
class AiCompletionPersistenceTest {
    @Autowired CropRepository crops;
    @Autowired CropGuidanceService guidance;
    @Autowired DailyWeatherImportService importer;
    @Autowired DailyWeatherRepository weather;
    @Autowired WeatherService weatherService;
    @Test void emptyOverrideSurvivesPersistenceAndSuppressesBuiltInRules() {
        var crop = crops.findByName("방울토마토").orElseThrow();
        assertFalse(CropGuidanceService.defaults(crop.getName()).isEmpty());
        guidance.replace(crop.getId(),new CropGuidanceService.Update(List.of()));
        assertTrue(guidance.read(crop.getId(),crop.getName()).isEmpty());
    }
    @Test void realMariaDbUpsertIsIdempotentAndProtectsHistoryFromForecasts() {
        var date=LocalDate.now().minusDays(1);
        importer.importDays(new ImportRequest(1,1,"https://example.invalid/synthetic-test",List.of(new Day(date,10,5,15))));
        importer.importDays(new ImportRequest(1,1,"https://example.invalid/synthetic-test",List.of(new Day(date,11,5,15))));
        var stored=weather.findByNxAndNyAndDate(1,1,date).orElseThrow();
        assertTrue(stored.isObserved()); assertEquals(11,stored.getAvgTemp());
        assertEquals(1,weather.findAllByNxAndNyAndDateBetweenOrderByDateAsc(1,1,date,date).size());
        assertEquals(11,weatherService.aggregateDaily(1,1,date).orElseThrow().getAvgTemp());
    }
}
