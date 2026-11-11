package community.likelion.growwithusserver.harvest.data.dto;

import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitResponseDto {
    private String visitId;
    private LocalDate date;
    // HH:mm
    private String startTime;
    private VisitStatus status;
}
