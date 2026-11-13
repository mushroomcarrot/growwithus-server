package community.likelion.growwithusserver.game.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueResponseDto {
    private String leagueId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int myRank;
    // 순위순
    private List<MemberDto> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberDto {
        private int rank;
        private String gardenId;
        // 가족 이름 (예: 민지네)
        private String nickname;
        private int weeklyXp;
        private boolean me;
    }
}
