package community.likelion.growwithusserver.game.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakCheckResponseDto {
    // 스트릭이 끊긴 텃밭 수
    private int broken;
}
