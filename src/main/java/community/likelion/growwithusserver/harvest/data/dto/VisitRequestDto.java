package community.likelion.growwithusserver.harvest.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitRequestDto {
    @NotBlank
    private String gardenId;

    @NotBlank
    private String slotId;

    // 방문 인원. 생략하면 1명으로 저장한다
    @Min(1)
    private Integer headcount;

    @Size(max = 200)
    private String memo;
}
