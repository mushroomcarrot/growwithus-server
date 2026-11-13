package community.likelion.growwithusserver.garden.service;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.growth.service.GrowthProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
@Component @Profile("!local-demo") @RequiredArgsConstructor
public class GardenGrowthProjection implements GrowthProjection {
    private final GardenRepository gardens;
    private final EvolutionEventService evolution;
    @Transactional(propagation = Propagation.MANDATORY)
    public String synchronize(Long id, Stage stage, double gdd, LocalDate forecast, String generatedEventId) {
        var garden = gardens.findByIdForUpdate(id).orElseThrow();
        var target = GrowthStage.valueOf(stage.name());
        var event = evolution.advanceStage(id, target);
        garden.setStage(target);
        garden.setAccumulatedGdd(gdd);
        garden.setExpectedHarvestDate(forecast);
        return event.map(e -> IdPrefix.EVENT.format(e.getId())).orElse(null);
    }
}
