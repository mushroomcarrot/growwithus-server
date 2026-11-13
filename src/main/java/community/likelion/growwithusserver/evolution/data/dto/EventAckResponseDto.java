package community.likelion.growwithusserver.evolution.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAckResponseDto {
    private String eventId;
    private int xpGained;
    private int totalXp;
    // 배지 보상이 없으면 null
    private BadgeDto badge;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BadgeDto {
        private String code;
        private String name;
    }
}
