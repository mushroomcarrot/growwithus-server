package community.likelion.growwithusserver.demo.data.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class DemoFastForwardRequestDto {
    @NotNull @Min(1) @Max(365)
    private Integer days;
}
