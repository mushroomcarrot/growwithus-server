package community.likelion.growwithusserver.game.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueSettleRequestDto {
    // 정산할 주에 속한 날. 비어 있으면 지난주
    private LocalDate weekStart;
}
