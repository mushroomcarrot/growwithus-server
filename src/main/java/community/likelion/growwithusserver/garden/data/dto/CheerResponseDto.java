package community.likelion.growwithusserver.garden.data.dto;

import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheerResponseDto {
    private int streakDays;
    private int xpGained;
    private int totalXp;
    private CharacterMood mood;
}
