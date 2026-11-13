package community.likelion.growwithusserver.voice.data.dto;

import community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus;
import community.likelion.growwithusserver.voice.data.enums.VoiceProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMemoResponseDto {
    private String voiceMemoId;
    private String transcript;
    private String summary;
    private List<String> issues;
    private VoiceMemoStatus status;
    private VoiceProcessingStatus processingStatus;
    private boolean retryable;
    private String processingError;
}
