package community.likelion.growwithusserver.growth.service;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.care.garden.CareGardenReader.Garden;
import community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;

/** 호출하는 도메인에서 텃밭 소유권을 확인한 후 사용하는 저장 상태 조회. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class GrowthStatusService {
    private final GrowthStateRepository states;
    public record Forecast(LocalDate expectedHarvestDate) {}

    public Optional<Forecast> forecast(Long gardenId) {
        return states.findById(gardenId).map(s -> new Forecast(s.getExpectedHarvestDate()));
    }

    public Stage stage(Garden garden) {
        return states.findById(garden.gardenId()).map(s -> s.getStage()).orElse(garden.stage());
    }

    public Stage expectedCheck(Garden garden) {
        var stored = states.findById(garden.gardenId());
        if (stored.isEmpty()) return garden.expectedStageToCheck();
        var state = stored.get();
        var expected = state.getStage();
        if (expected != Stage.FLOWER && expected != Stage.FRUIT) return null;
        return state.getCalibrationStage() != null && state.getCalibrationStage().ordinal() >= expected.ordinal()
                ? null : expected;
    }
}
