package community.likelion.growwithusserver.growth;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.care.garden.CareGardenReader.Garden;
import community.likelion.growwithusserver.growth.data.entity.GrowthState;
import community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository;
import community.likelion.growwithusserver.growth.service.GrowthStatusService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.repository.OutingRepository;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import community.likelion.growwithusserver.harvest.service.*;
import community.likelion.growwithusserver.support.FakeRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GrowthStatusTest {
    @Test void predictedFruitDoesNotBecomeHarvestPermissionWithoutCaretakerConfirmation() {
        assertTrue(growth.harvestable(1L,true));
        state=GrowthState.builder().stage(Stage.FRUIT).build();
        assertFalse(growth.harvestable(1L,true));
        state.setCalibrationStage(Stage.FRUIT);
        assertTrue(growth.harvestable(1L,true));
        assertFalse(growth.harvestable(1L,false));
    }
    private GrowthState state;
    private int reads;
    private final GrowthStatusService growth = new GrowthStatusService(FakeRepository.of(GrowthStateRepository.class, (method, args) -> {
        if (!method.equals("findById")) throw new AssertionError(method);
        reads++; return Optional.ofNullable(state);
    }));
    private final Garden garden = new Garden(1L, "care", "owner", "A1", "crop", "family", 1, 1, Stage.SPROUT, Stage.FLOWER, List.of());

    @Test void usesExistingDomainWhenSimulationIsNotAvailable() {
        assertEquals(Stage.SPROUT, growth.stage(garden));
        assertEquals(Stage.FLOWER, growth.expectedCheck(garden));
        assertTrue(growth.forecast(1L).isEmpty());
    }
    @Test void stopsAskingAfterConfirmationAndAsksAtNextMajorStage() {
        state = GrowthState.builder().stage(Stage.FLOWER).build();
        assertEquals(Stage.FLOWER, growth.expectedCheck(garden));
        state.setCalibrationStage(Stage.FLOWER);
        assertNull(growth.expectedCheck(garden));
        state.setStage(Stage.FRUIT);
        assertEquals(Stage.FRUIT, growth.expectedCheck(garden));
        state.setCalibrationStage(Stage.FRUIT);
        assertNull(growth.expectedCheck(garden));
    }
    @Test void storedEarlyStageDoesNotUseStaleDomainHint() {
        state = GrowthState.builder().stage(Stage.SPROUT).build();
        assertNull(growth.expectedCheck(garden));
    }
    private HarvestService harvest(HarvestGarden original) {
        var reader = new HarvestGardenService(id -> Optional.of(original));
        var outings = FakeRepository.of(OutingRepository.class, (method, args) -> { throw new AssertionError(method); });
        return new HarvestService(reader, outings, growth);
    }
    @Test void harvestUsesNewForecastWithoutMutatingDomainOrDeclaringRipeness() {
        var original = HarvestGarden.builder().gardenId(1L).ownerUsername("owner").characterName("crop")
                .expectedHarvestDate(LocalDate.now().plusDays(30)).harvestable(false).build();
        state = GrowthState.builder().stage(Stage.FRUIT).expectedHarvestDate(LocalDate.now().plusDays(5)).build();
        var result = harvest(original).getHarvest("owner", "gdn_1").getData();
        assertEquals(state.getExpectedHarvestDate(), result.getExpectedDate()); assertEquals(5, result.getDDay());
        assertFalse(result.isHarvestable()); assertTrue(result.getMessage().contains("" + result.getExpectedDate().getDayOfMonth()));
        assertEquals(LocalDate.now().plusDays(30), original.getExpectedHarvestDate());
    }
    @Test void unknownNewForecastReplacesOldDateInsteadOfShowingFalsePrecision() {
        var original = HarvestGarden.builder().gardenId(1L).ownerUsername("owner").expectedHarvestDate(LocalDate.now().plusDays(30)).build();
        assertEquals(original.getExpectedHarvestDate(), harvest(original).getHarvest("owner", "gdn_1").getData().getExpectedDate());
        state = GrowthState.builder().stage(Stage.SPROUT).build();
        var result = harvest(original).getHarvest("owner", "gdn_1").getData();
        assertNull(result.getExpectedDate()); assertNull(result.getDDay());
    }
    @Test void checksOwnershipBeforeReadingGrowthState() {
        var original = HarvestGarden.builder().gardenId(1L).ownerUsername("owner").build();
        assertThrows(GardenAccessDeniedException.class, () -> harvest(original).getHarvest("other", "gdn_1"));
        assertEquals(0, reads);
    }
}
