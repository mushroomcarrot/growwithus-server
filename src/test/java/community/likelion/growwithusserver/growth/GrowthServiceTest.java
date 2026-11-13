package community.likelion.growwithusserver.growth;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.*;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.growth.data.entity.*;
import community.likelion.growwithusserver.growth.data.repository.*;
import community.likelion.growwithusserver.growth.service.GrowthService;
import community.likelion.growwithusserver.support.FakeRepository;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GrowthServiceTest {
    private GrowthState state;
    private final List<Object> notifications = new ArrayList<>();
    private final List<GrowthEvent> events = new ArrayList<>();
    private List<WeatherDay> weather = List.of(new WeatherDay(LocalDate.now().minusDays(1), 25));
    private final CareGardenReader.Garden garden = new CareGardenReader.Garden(1L, "care", "owner", "A1", "crop", "family", 1, 1,
            Stage.SEED, Stage.FLOWER, List.of(), LocalDate.now().minusDays(1), 10,
            Map.of(Stage.SEED, 0.0, Stage.SPROUT, 10.0, Stage.FLOWER, 20.0, Stage.FRUIT, 30.0));
    private final CareGardenReader gardens = new CareGardenReader() {
        public List<Garden> findAssigned(String username) { return List.of(garden); }
        public List<Garden> findAll() { return List.of(garden); }
        public Optional<Garden> findGarden(Long id) { return id.equals(1L) ? Optional.of(garden) : Optional.empty(); }
        public Optional<Garden> findGardenForUpdate(Long id) { return findGarden(id); }
    };
    private GrowthService service() {
        var states = FakeRepository.of(GrowthStateRepository.class, (method, args) -> switch (method) {
            case "findById" -> Optional.ofNullable(state);
            case "save" -> { state = (GrowthState) args[0]; yield state; }
            default -> throw new AssertionError(method);
        });
        var eventRepo = FakeRepository.of(GrowthEventRepository.class, (method, args) -> {
            if (!method.equals("save")) throw new AssertionError(method);
            var event = (GrowthEvent) args[0]; event.setId((long) events.size() + 1); events.add(event); return event;
        });
        return new GrowthService(gardens, (nx, ny, from, to) -> weather, states, eventRepo, null, null, notifications::add,
                (id, stage, gdd, forecast, event) -> event);
    }
    @Test void persistsGrowthAndEmitsStageChangeOnlyOnce() {
        var service = service();
        assertEquals(Stage.SPROUT, service.get("owner", 1L).getStage());
        assertEquals(15.0, state.getAccumulatedGdd());
        service.get("owner", 1L);
        assertEquals(1, events.size()); assertEquals(1, notifications.size());
    }
    @Test void rejectsMissingWeatherWithoutReplacingPreviousState() {
        var service = service(); service.get("owner", 1L);
        weather = List.of();
        assertThrows(VoiceMemoException.class, () -> service.get("owner", 1L));
        assertEquals(Stage.SPROUT, state.getStage()); assertEquals(15.0, state.getAccumulatedGdd());
        assertEquals(1, events.size());
    }
    @Test void allowsAssignedCaretakerToCalibrateAndKeepsAnchorOnRefresh() {
        var service = service();
        var result = service.check("care", 1L, new GrowthDtos.CheckRequest(GrowthDtos.Observed.FLOWER, null));
        assertTrue(result.isCalibrated()); assertEquals(Stage.FLOWER, result.getStage());
        assertEquals(20.0, state.getAccumulatedGdd());
        assertEquals(Stage.FLOWER, service.get("owner", 1L).getStage());
        assertEquals(1, events.size());
    }
    @Test void noChangeDoesNotInventCalibrationOrEvent() {
        var result = service().check("care", 1L, new GrowthDtos.CheckRequest(GrowthDtos.Observed.NO_CHANGE, null));
        assertFalse(result.isCalibrated()); assertNull(state); assertTrue(events.isEmpty());
    }
    @Test void rejectsOtherOwnersAndCaretakersBeforeWriting() {
        assertThrows(VoiceMemoException.class, () -> service().get("other", 1L));
        assertThrows(VoiceMemoException.class, () -> service().check("other", 1L,
                new GrowthDtos.CheckRequest(GrowthDtos.Observed.FRUIT, null)));
        assertNull(state); assertTrue(events.isEmpty());
    }

    @Test void demoSimulationUsesVirtualDaysWithoutRealWeather() {
        state = GrowthState.builder().gardenId(1L).stage(Stage.FLOWER).demoDays(18).build();
        weather = List.of();
        var detail = service().get("owner", 1L);
        assertEquals(Stage.FRUIT, detail.getStage());
        assertEquals(LocalDate.now(), detail.getExpectedHarvestDate());
        assertTrue(detail.getExplanation().contains("시연용"));
        assertNull(state.getCalibrationStage());
        assertEquals(Stage.FRUIT, service().get("owner", 1L).getStage());
    }

    @Test void demoSimulationRequiresCaretakerConfirmationForHarvest() {
        state = GrowthState.builder().gardenId(1L).stage(Stage.FLOWER).demoDays(9).build();
        var detail = service().get("owner", 1L);
        assertEquals(Stage.FLOWER, detail.getStage());
        assertEquals(LocalDate.now().plusDays(9), detail.getExpectedHarvestDate());
        service().check("care", 1L, new GrowthDtos.CheckRequest(GrowthDtos.Observed.FRUIT, null));
        assertEquals(Stage.FRUIT, state.getCalibrationStage());
        assertEquals(Stage.FRUIT, service().get("owner", 1L).getStage());
    }
}
