package community.likelion.growwithusserver.growth.service;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import java.time.LocalDate;
public interface GrowthProjection {
    String synchronize(Long gardenId, Stage stage, double accumulatedGdd, LocalDate expectedHarvestDate, String generatedEventId);
}
