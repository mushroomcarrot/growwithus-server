package community.likelion.growwithusserver.farm.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotPreviewResponseDto {
    private String farmId;
    private String plotCode;
    // 분양받을 수 있는 칸인지. 이미 분양된 칸은 C4093으로 응답하므로 성공 응답에서는 항상 true다
    private boolean available;
    private CropSummary crop;
    // 담당 관리사. 아직 배정 전이면 null
    private CaretakerSummary caretaker;
    // 월 이용료 (가정치)
    private int monthlyFee;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CropSummary {
        private String cropId;
        private String name;
        private String characterName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaretakerSummary {
        // 관리사 프로필이 없으면 null
        private String caretakerId;
        private String name;
        private String avatarUrl;
    }
}
