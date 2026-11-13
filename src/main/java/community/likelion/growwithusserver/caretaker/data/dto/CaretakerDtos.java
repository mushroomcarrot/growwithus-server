package community.likelion.growwithusserver.caretaker.data.dto;
import community.likelion.growwithusserver.user.data.enums.NextStep;
import jakarta.validation.constraints.*;
import lombok.*;
public final class CaretakerDtos {
    private CaretakerDtos() {}
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Register {
        @NotBlank @Size(max = 20) private String name;
        @NotBlank @Pattern(regexp = "farm_[1-9][0-9]*") private String farmId;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Profile {
        private String caretakerId;
        private String name;
        private String farmId;
        private NextStep nextStep;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Avatar {
        private String avatarJobId;
        private community.likelion.growwithusserver.caretaker.data.enums.AvatarStatus status;
        // PENDING이면 null, DONE이면 만든 그림, FAILED면 지금 쓰는 아바타 (처음이면 기본 아바타)
        private String avatarUrl;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Summary {
        private String month;
        private long completedTasks;
        private long gardenCount;
        private long workDays;
        private long heartsReceived;
        private long heartsReceivedToday;
        private long expectedAllowance;
        private String allowanceNote;
    }
}
