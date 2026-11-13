package community.likelion.growwithusserver.farm.data.dto;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropResponseDto {
    private String cropId;
    private String name;
    private String characterName;
    private double baseTemp;
    private List<StageDto> stages;
    private String schedule;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageDto {
        private GrowthStage stage;
        private int level;
        private double requiredGdd;
    }

    public static CropResponseDto from(Crop crop) {
        return CropResponseDto.builder()
                .cropId(IdPrefix.CROP.format(crop.getId()))
                .name(crop.getName())
                .characterName(crop.getCharacterName())
                .baseTemp(crop.getBaseTemp())
                .stages(Arrays.stream(GrowthStage.values())
                        .map(stage -> new StageDto(stage, stage.getLevel(), crop.requiredGdd(stage)))
                        .toList())
                .schedule(crop.getSchedule())
                .build();
    }
}
