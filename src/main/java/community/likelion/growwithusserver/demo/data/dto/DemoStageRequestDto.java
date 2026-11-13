package community.likelion.growwithusserver.demo.data.dto;

import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class DemoStageRequestDto {
    @NotNull private GrowthStage stage;
}
