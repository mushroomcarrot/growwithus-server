package community.likelion.growwithusserver.game.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakRemindResponseDto {
    // 이번 실행으로 스트릭 위험 알림을 보낸 주인 수
    private int reminded;
}
