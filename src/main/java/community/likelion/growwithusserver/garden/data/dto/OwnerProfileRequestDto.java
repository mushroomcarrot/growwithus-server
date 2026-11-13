package community.likelion.growwithusserver.garden.data.dto;

import community.likelion.growwithusserver.farm.data.enums.District;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfileRequestDto {
    @NotBlank
    @Size(max = 20)
    private String nickname;

    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20)
    private Integer familySize;

    // SUJI, GIHEUNG만 허용
    @NotNull
    private District district;

    @NotBlank
    private String farmId;

    @NotBlank
    @Size(max = 20)
    private String plotCode;
}
