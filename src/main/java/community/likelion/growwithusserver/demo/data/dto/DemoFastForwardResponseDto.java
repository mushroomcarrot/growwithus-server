package community.likelion.growwithusserver.demo.data.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoFastForwardResponseDto {
    private String stage;
    private String eventId;
    private LocalDate expectedHarvestDate;
}
