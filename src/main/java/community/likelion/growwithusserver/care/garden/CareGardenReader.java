package community.likelion.growwithusserver.care.garden;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules.Guidance;
import java.util.List;
import java.util.Optional;

/** 농장 및 텃밭 도메인이 실제 데이터를 제공한다. 목록과 잠금 조회의 담당자가 같아야 한다. */
public interface CareGardenReader {
    record Garden(Long gardenId, String caretakerUsername, String ownerUsername, String plotCode,
                  String cropName, String ownerNickname, int nx, int ny, Stage stage,
                  Stage expectedStageToCheck, List<Guidance> guidance,
                  java.time.LocalDate startedOn, double baseTemp, java.util.Map<Stage, Double> requiredGdd) {
        public Garden { guidance = List.copyOf(guidance); requiredGdd = java.util.Map.copyOf(requiredGdd); }
        public Garden(Long gardenId, String caretakerUsername, String ownerUsername, String plotCode,
                      String cropName, String ownerNickname, int nx, int ny, Stage stage,
                      Stage expectedStageToCheck, List<Guidance> guidance) {
            this(gardenId, caretakerUsername, ownerUsername, plotCode, cropName, ownerNickname, nx, ny,
                    stage, expectedStageToCheck, guidance, null, Double.NaN, java.util.Map.of());
        }
    }
    List<Garden> findAssigned(String caretakerUsername);
    List<Garden> findAll();
    Optional<Garden> findGarden(Long gardenId);
    Optional<Garden> findGardenForUpdate(Long gardenId);
}
