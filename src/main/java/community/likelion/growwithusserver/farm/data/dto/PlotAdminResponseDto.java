package community.likelion.growwithusserver.farm.data.dto;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlotAdminResponseDto {
    private String farmId;
    private String plotCode;
    private String cropId;
    private String cropName;
    // 담당 관리사의 로그인 아이디. 배정 전이면 null
    private String caretakerUsername;
    // 분양 여부
    private boolean assigned;

    public static PlotAdminResponseDto of(Long farmId, Plot plot) {
        return PlotAdminResponseDto.builder()
                .farmId(IdPrefix.FARM.format(farmId))
                .plotCode(plot.getPlotCode())
                .cropId(IdPrefix.CROP.format(plot.getCrop().getId()))
                .cropName(plot.getCrop().getName())
                .caretakerUsername(plot.getCaretaker() == null ? null : plot.getCaretaker().getUsername())
                .assigned(plot.isAssigned())
                .build();
    }
}
