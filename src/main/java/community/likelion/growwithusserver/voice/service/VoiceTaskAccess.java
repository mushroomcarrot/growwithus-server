package community.likelion.growwithusserver.voice.service;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import community.likelion.growwithusserver.voice.task.VoiceTaskReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceTaskAccess {
    private final VoiceTaskReader taskReader;

    public VoiceTaskReader.VoiceTask requireOwned(Long taskId, String username) {
        return validateOwned(taskReader.findTask(taskId), taskId, username);
    }

    public VoiceTaskReader.VoiceTask requireOwnedForUpdate(Long taskId, String username) {
        return validateOwned(taskReader.findTaskForUpdate(taskId), taskId, username);
    }

    private VoiceTaskReader.VoiceTask validateOwned(java.util.Optional<VoiceTaskReader.VoiceTask> found,
                                                   Long taskId, String username) {
        VoiceTaskReader.VoiceTask task = found
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!taskId.equals(task.taskId()) || username == null || !username.equals(task.caretakerUsername())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        return task;
    }

    public void requireOpen(Long taskId, String username) {
        if (requireOwned(taskId, username).completed()) {
            throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
        }
    }

    public void requireOpenForUpdate(Long taskId, String username) {
        if (requireOwnedForUpdate(taskId, username).completed()) {
            throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
        }
    }
}
