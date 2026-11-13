package community.likelion.growwithusserver.evolution.data.dto;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.time.DateTimes;
import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionEventResponseDto {
    private String eventId;
    private GrowthStage fromStage;
    private GrowthStage toStage;
    private OffsetDateTime occurredAt;
    private RewardDto reward;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RewardDto {
        private int xp;
        // 배지 보상이 없으면 null
        private String badgeCode;
    }

    public static EvolutionEventResponseDto from(EvolutionEvent event) {
        return EvolutionEventResponseDto.builder()
                .eventId(IdPrefix.EVENT.format(event.getId()))
                .fromStage(event.getFromStage())
                .toStage(event.getToStage())
                .occurredAt(DateTimes.toOffset(event.getOccurredAt()))
                .reward(new RewardDto(event.getRewardXp(), event.getRewardBadgeCode()))
                .build();
    }
}
