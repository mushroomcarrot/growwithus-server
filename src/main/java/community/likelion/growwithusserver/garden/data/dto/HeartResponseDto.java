package community.likelion.growwithusserver.garden.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartResponseDto {
    private String careLogId;
    private boolean hearted;
    private int xpGained;
}
