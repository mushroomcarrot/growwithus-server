package community.likelion.growwithusserver.farm.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmSyncResponseDto {
    // 원본에서 읽은 농장 수
    private int totalFarms;
    private int addedFarms;
    private int updatedFarms;
    // 주소에서 읍면동을 찾지 못해 넣지 못한 농장 이름
    private List<String> skippedFarms;
}
