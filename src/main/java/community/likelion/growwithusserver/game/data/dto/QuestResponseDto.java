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
public class QuestResponseDto {
    private String questId;
    private String title;
    private int progress;
    private int goal;
    private int rewardXp;
    private QuestStatus status;
}
