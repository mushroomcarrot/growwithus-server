package community.likelion.growwithusserver.farm.data.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// PUT /internal/farms/{farmId}/plots/{plotCode}. 값을 생략한 항목은 그대로 둔다.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlotAdminRequestDto {
    // 심을 작물 (예: crop_1). 칸을 새로 만들 때는 필수
    @Pattern(regexp = "crop_[1-9][0-9]*")
    private String cropId;

    // 담당 관리사의 로그인 아이디. 역할이 CARETAKER인 회원이어야 한다
    @Size(min = 1, max = 20)
    private String caretakerUsername;
}
