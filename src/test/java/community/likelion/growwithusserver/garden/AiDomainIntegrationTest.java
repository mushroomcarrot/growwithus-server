package community.likelion.growwithusserver.garden;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.entity.*;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.care.service.StoredGardenCareLogReader;
import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import community.likelion.growwithusserver.farm.data.entity.*;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.*;
import community.likelion.growwithusserver.garden.service.*;
import community.likelion.growwithusserver.user.data.entity.User;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiDomainIntegrationTest {
    @Test void mapsRealOwnershipCropAndLockedGarden() {
        var gardens = mock(GardenRepository.class);
        var owners = mock(OwnerProfileRepository.class);
        var owner = new User(); owner.setId(10L); owner.setUsername("owner"); owner.setName("family");
        var caretaker = new User(); caretaker.setUsername("caretaker");
        var farm = new Farm(); farm.setNx(64); farm.setNy(119);
        var crop = new Crop(); crop.setName("crop"); crop.setBaseTemp(10); crop.setSproutGdd(20); crop.setFlowerGdd(100); crop.setFruitGdd(250);
        var plot = new Plot(); plot.setPlotCode("B14"); plot.setFarm(farm);
        var garden = Garden.builder().id(7L).owner(owner).caretaker(caretaker).plot(plot).crop(crop).startedOn(LocalDate.now()).build();
        when(owners.findByUserId(10L)).thenReturn(Optional.empty());
        when(gardens.findByIdForUpdate(7L)).thenReturn(Optional.of(garden));
        when(gardens.findAllByCaretakerUsername("caretaker")).thenReturn(List.of(garden));
        var guidance = mock(community.likelion.growwithusserver.care.guidance.CropGuidanceService.class);
        when(guidance.read(any(), eq("crop"))).thenReturn(List.of());
        var reader = new GardenCareReader(gardens, owners, guidance);
        var mapped = reader.findGardenForUpdate(7L).orElseThrow();
        assertEquals("owner", mapped.ownerUsername());
        assertEquals("caretaker", mapped.caretakerUsername());
        assertEquals("B14", mapped.plotCode()); assertEquals(250d, mapped.requiredGdd().get(Stage.FRUIT));
        assertEquals(1, reader.findAssigned("caretaker").size());
        verify(gardens).findByIdForUpdate(7L); verify(gardens, never()).findDetail(any());
        assertTrue(reader.findAssigned(null).isEmpty());
    }

    @Test void completedCareBridgePreservesRawSpeechAndSummarySeparately() {
        var logs = mock(CareLogRepository.class); var tasks = mock(TaskRepository.class);
        var at = LocalDateTime.of(2026, 11, 13, 9, 0);
        var log = CareLog.builder().id(1L).gardenId(7L).taskId(2L).caretakerUsername("caretaker")
                .transcript("raw speech").summary("edited summary").createdAt(at).build();
        when(logs.findById(1L)).thenReturn(Optional.of(log));
        when(tasks.findById(2L)).thenReturn(Optional.of(Task.builder().type(TaskRules.Type.CHECK).build()));
        var mapped = new StoredGardenCareLogReader(logs, tasks).findCareLog(1L).orElseThrow();
        assertEquals("raw speech", mapped.transcript()); assertEquals("edited summary", mapped.summary());
        assertEquals(7L, mapped.gardenId()); assertEquals(List.of("CHECK"), mapped.taskTypes());
    }

    @Test void growthUpdatesCanonicalGardenAndReturnsTheOwnersEvolutionEventId() {
        var gardens = mock(GardenRepository.class); var evolution = mock(EvolutionEventService.class);
        var garden = Garden.builder().id(7L).build();
        when(gardens.findByIdForUpdate(7L)).thenReturn(Optional.of(garden));
        when(evolution.advanceStage(7L, GrowthStage.FLOWER)).thenReturn(Optional.of(EvolutionEvent.builder().id(42L).build()));
        var forecast = LocalDate.of(2026, 12, 1);
        var result = new GardenGrowthProjection(gardens, evolution).synchronize(7L, Stage.FLOWER, 110, forecast, "evt_1");
        assertEquals("evt_42", result); assertEquals(GrowthStage.FLOWER, garden.getStage());
        assertEquals(110, garden.getAccumulatedGdd()); assertEquals(forecast, garden.getExpectedHarvestDate());
        when(evolution.advanceStage(7L, GrowthStage.FLOWER)).thenReturn(Optional.empty());
        assertNull(new GardenGrowthProjection(gardens, evolution).synchronize(7L, Stage.FLOWER, 110, forecast, "evt_1"));
    }
}
