package community.likelion.growwithusserver.care.data.dto;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules.Type;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import lombok.*;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

public final class TaskDtos {
    private TaskDtos() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Today {
        private LocalDate date;
        private String weatherNotice;
        private List<Type> skippedTypes;
        private int totalMinutes;
        private List<GardenCard> gardens;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GardenCard {
        private String gardenId;
        private String plotCode;
        private String cropName;
        private String ownerNickname;
        private List<Item> tasks;
        private StageCheck stageCheck;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private String taskId;
        private Type type;
        private String title;
        private String description;
        private int minutes;
        private TaskStatus status;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StageCheck { private boolean required; private Stage expectedStage; private String gardenId; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompleteRequest {
        @Pattern(regexp = "pht_[1-9][0-9]*") private String photoId;
        @Pattern(regexp = "vm_[1-9][0-9]*") private String voiceMemoId;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Completed {
        private String taskId;
        private TaskStatus status;
        private String careLogId;
        private long remainingTasks;
        private StageCheck stageCheck;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Photo { private String photoId; private String photoUrl; private AiCheck aiCheck; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AiCheck { private boolean matched; private String message; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BatchRequest {
        private LocalDate date;
        @jakarta.validation.constraints.Size(max = 200)
        private List<@Pattern(regexp = "gdn_[1-9][0-9]*") String> gardenIds;
    }
}
