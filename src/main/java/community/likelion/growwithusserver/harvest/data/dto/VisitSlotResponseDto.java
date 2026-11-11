package community.likelion.growwithusserver.harvest.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitSlotResponseDto {
    private String slotId;
    private LocalDate date;
    // HH:mm
    private String startTime;
    private String endTime;
    private boolean available;
}
