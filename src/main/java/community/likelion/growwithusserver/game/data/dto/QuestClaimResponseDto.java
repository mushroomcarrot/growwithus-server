package community.likelion.growwithusserver.game.data.dto;

import community.likelion.growwithusserver.game.data.enums.QuestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestClaimResponseDto {
    private String questId;
    private int xpGained;
    private int totalXp;
    private QuestStatus status;
}
