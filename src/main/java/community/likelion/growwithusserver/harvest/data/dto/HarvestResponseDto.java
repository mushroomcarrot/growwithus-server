package community.likelion.growwithusserver.harvest.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarvestResponseDto {
    private community.likelion.growwithusserver.farm.data.enums.GrowthStage stage;
    // AI 예측 수확일. 아직 계산 전이면 null
    private LocalDate expectedDate;
    // 수확일까지 남은 일수. 수확일이 지났으면 0, 수확일이 없으면 null
    private Integer dDay;
    private boolean harvestable;
    // 텃밭을 시작한 날부터 오늘까지 (시작한 날을 1일로 센다)
    private int daysCaredTogether;
    private int careLogCount;
    private String caretakerName;
    private String message;
    // 오늘 이후의 가장 가까운 방문 예약. 없으면 null
    private VisitResponseDto visit;
}
