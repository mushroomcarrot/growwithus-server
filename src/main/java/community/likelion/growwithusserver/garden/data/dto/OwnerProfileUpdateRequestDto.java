package community.likelion.growwithusserver.garden.data.dto;

import community.likelion.growwithusserver.farm.data.enums.District;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 바꿀 필드만 보낸다. null인 필드는 그대로 둔다
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfileUpdateRequestDto {
    @Size(max = 20)
    @Pattern(regexp = ".*\\S.*")
    private String nickname;

    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20)
    private Integer familySize;

    // SUJI, GIHEUNG만 허용
    private District district;
}
