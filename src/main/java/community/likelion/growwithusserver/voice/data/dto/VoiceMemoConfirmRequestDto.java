package community.likelion.growwithusserver.voice.data.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMemoConfirmRequestDto {
    @Size(min = 1, max = 1000)
    private String summary;
}
