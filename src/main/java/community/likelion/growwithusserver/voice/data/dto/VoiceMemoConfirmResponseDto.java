package community.likelion.growwithusserver.voice.data.dto;

import community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMemoConfirmResponseDto {
    private String voiceMemoId;
    private VoiceMemoStatus status;
}
