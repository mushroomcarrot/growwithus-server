package community.likelion.growwithusserver.garden.data.dto;

import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.user.data.enums.NextStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfileCreateResponseDto {
    private int familySize;
    private String gardenId;
    private String characterName;
    private GrowthStage stage;
    private NextStep nextStep;
}
