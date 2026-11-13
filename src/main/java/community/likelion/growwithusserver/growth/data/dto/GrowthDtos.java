package community.likelion.growwithusserver.growth.data.dto;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.*;
import java.util.List;
public final class GrowthDtos {
    private GrowthDtos() {}
    public enum Observed { FLOWER, FRUIT, NO_CHANGE }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CheckRequest {
        @NotNull private Observed observed;
        @Pattern(regexp = "pht_[1-9][0-9]*") private String photoId;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detail {
        private Stage stage; private int level; private double accumulatedGdd;
        private Stage nextStage; private Double nextStageGdd; private double progress;
        private LocalDate expectedHarvestDate; private OffsetDateTime calibratedAt;
        private String explanation; private List<GrowthCalculator.DailyGdd> dailyGdd;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Checked { private Stage stage; private boolean calibrated; private OffsetDateTime calibratedAt; private String eventId; }
}
