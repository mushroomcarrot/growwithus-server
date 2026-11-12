package community.likelion.growwithusserver.voice.service;

import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.ai.service.VoiceMemoAnalysisService;
import community.likelion.growwithusserver.common.file.FileUploadService;
import community.likelion.growwithusserver.common.file.exception.FileUploadFailedException;
import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import community.likelion.growwithusserver.common.ai.AiClientException;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoConfirmRequestDto;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoConfirmResponseDto;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class VoiceMemoService {
    private final VoiceTaskAccess taskAccess;
    private final FileUploadService fileUploadService;
    private final VoiceMemoAnalysisService analysisService;
    private final VoiceMemoStore store;

    // 모델과 파일 처리는 DB 트랜잭션 밖에서 수행한다.
    public GlobalResponse<VoiceMemoResponseDto> create(String username, String publicTaskId, MultipartFile audio) {
        Long taskId = IdPrefix.TASK.parse(publicTaskId);
        taskAccess.requireOpen(taskId, username);
        fileUploadService.validateAudio(audio);
        String audioUrl = fileUploadService.uploadAudio(audio, "voice-memos");
        VoiceMemoStore.Attempt attempt = store.createPending(username, taskId, audioUrl);
        try {
            return process(username, attempt, audio.getBytes(), audio.getOriginalFilename());
        } catch (IOException ex) {
            return GlobalResponse.ok(store.fail(username, attempt, ErrorCode.FILE_UPLOAD_FAILED));
        }
    }

    public GlobalResponse<VoiceMemoResponseDto> retry(String username, String publicMemoId) {
        VoiceMemoStore.Attempt attempt = store.beginRetry(username, IdPrefix.VOICE_MEMO.parse(publicMemoId));
        try {
            return process(username, attempt, attempt.transcript() == null ? fileUploadService.readStoredAudio(attempt.audioUrl()) : null,
                    "recording." + attempt.audioUrl().substring(attempt.audioUrl().lastIndexOf('.') + 1));
        } catch (FileUploadFailedException ex) {
            return GlobalResponse.ok(store.fail(username, attempt, ErrorCode.FILE_UPLOAD_FAILED));
        }
    }

    private GlobalResponse<VoiceMemoResponseDto> process(String username, VoiceMemoStore.Attempt attempt, byte[] audio, String filename) {
        try {
            VoiceAnalysis.Result analysis = attempt.transcript() == null
                    ? analysisService.transcribeAndAnalyze(audio, filename) : analysisService.analyze(attempt.transcript());
            return GlobalResponse.ok(store.finish(username, attempt, analysis));
        } catch (AiClientException ex) {
            return GlobalResponse.ok(store.fail(username, attempt, ErrorCode.EXTERNAL_API_ERROR));
        } catch (FileNotSupportedException ex) {
            return GlobalResponse.ok(store.fail(username, attempt, ErrorCode.FILE_NOT_SUPPORTED));
        }
    }

    public GlobalResponse<VoiceMemoConfirmResponseDto> confirm(String username, String publicMemoId,
                                                              VoiceMemoConfirmRequestDto request) {
        return GlobalResponse.ok(store.confirm(username, IdPrefix.VOICE_MEMO.parse(publicMemoId),
                request == null ? null : request.getSummary()));
    }
}
