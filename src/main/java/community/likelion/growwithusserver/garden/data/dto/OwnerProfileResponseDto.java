package community.likelion.growwithusserver.garden.data.dto;

import community.likelion.growwithusserver.farm.data.enums.District;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfileResponseDto {
    private String nickname;
    private District district;
    // 텃밭이 없으면 null
    private String gardenId;
}
