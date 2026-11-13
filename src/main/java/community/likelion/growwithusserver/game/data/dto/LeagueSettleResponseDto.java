package community.likelion.growwithusserver.game.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueSettleResponseDto {
    // 이번 실행으로 정산한 리그 수. 이미 정산한 리그는 세지 않는다
    private int settledLeagues;
    // 새 주에 만든 퀘스트 수
    private int createdQuests;
}
