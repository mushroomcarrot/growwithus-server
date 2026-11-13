package community.likelion.growwithusserver.care;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.ai.logic.TaskRules.*;
import community.likelion.growwithusserver.care.guidance.*;
import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CropGuidanceServiceTest {
    @Test void tomatoGuidanceIsUsableAndStillSkipsWaterWhenRaining() {
        var guidance = CropGuidanceService.defaults("방울토마토");
        assertEquals(2, guidance.size());
        assertTrue(CropGuidanceService.defaults("unknown crop").isEmpty());
        var context = new TaskRules.Context(LocalDate.now(), Stage.FLOWER, true, true, guidance, List.of());
        var rules = new TaskRules();
        var plan = rules.validate(context, rules.fallbackCandidates(context), 120);
        assertEquals(List.of(Type.SUPPORT), plan.tasks().stream().map(Task::type).toList());
        assertTrue(plan.skippedTypes().contains(Type.WATER));
        assertTrue(plan.tasks().stream().allMatch(t -> t.sourceId().startsWith("https://www.yongin.go.kr/")));
    }
    @Test void explicitlyClearedGuidanceDoesNotReappearFromDefaults() {
        var rows = mock(CropGuidanceRepository.class);
        when(rows.findById(1L)).thenReturn(Optional.of(CropGuidanceSettings.builder().cropId(1L).rulesJson("[]").build()));
        var service = new CropGuidanceService(mock(CropRepository.class), rows, JsonMapper.builder().build());
        assertTrue(service.read(1L, "방울토마토").isEmpty());
    }
    @Test void updatesLockTheActualCropAndPersistTheWholeValidatedSet() {
        var crops = mock(CropRepository.class); var rows = mock(CropGuidanceRepository.class);
        when(crops.findByIdForUpdate(1L)).thenReturn(Optional.of(new Crop()));
        var service = new CropGuidanceService(crops, rows, JsonMapper.builder().build());
        var update = new CropGuidanceService.Update(CropGuidanceService.defaults("토마토"));
        service.replace(1L, update);
        verify(crops).findByIdForUpdate(1L);
        verify(rows).save(argThat(row -> row.getCropId().equals(1L) && row.getRulesJson().contains("SUPPORT")));
    }
    @Test void rejectsDuplicateTaskTypesAndNonPublicSourceUris() {
        var water = CropGuidanceService.defaults("토마토").getFirst();
        assertThrows(IllegalArgumentException.class, () -> new CropGuidanceService.Update(List.of(water, water)));
        var invalid = new Guidance(Type.CHECK, Set.of(Stage.SEED), 1, "check", "check", 5, "file:///private");
        assertThrows(IllegalArgumentException.class, () -> new CropGuidanceService.Update(List.of(invalid)));
    }
}
