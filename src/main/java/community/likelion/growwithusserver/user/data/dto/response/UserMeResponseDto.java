package community.likelion.growwithusserver.user.data.dto.response;

import community.likelion.growwithusserver.user.data.enums.NextStep;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMeResponseDto {
    private String userId;
    private String name;
    private String username;
    private UserRole role;
    private NextStep nextStep;
    // 주인 프로필 요약. 주인 프로필을 등록하기 전이면 null이다.
    private OwnerSummary owner;
    // 관리사 프로필 요약. 관리사 프로필 도메인이 생기기 전까지는 항상 null이다.
    private CaretakerSummary caretaker;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerSummary {
        private String nickname;
        private String district;
        private String gardenId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaretakerSummary {
        private String caretakerId;
        private String name;
        private String farmId;
        private String avatarUrl;
        private long completedTasks;
    }
}
