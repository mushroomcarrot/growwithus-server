package community.likelion.growwithusserver.voice.service;

import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoConfirmResponseDto;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoResponseDto;
import community.likelion.growwithusserver.voice.data.entity.VoiceMemo;
import community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus;
import community.likelion.growwithusserver.voice.data.enums.VoiceProcessingStatus;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import community.likelion.growwithusserver.voice.data.repository.VoiceMemoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VoiceMemoStore {
    private final VoiceMemoRepository repository;
    private final VoiceTaskAccess taskAccess;
    private final community.likelion.growwithusserver.common.file.FileUploadService uploads;
    @org.springframework.beans.factory.annotation.Value("${external.http.read-timeout:30s}")
    private java.time.Duration readTimeout = java.time.Duration.ofSeconds(30);

    public record Attempt(Long memoId, Long taskId, String token, String audioUrl, String transcript) {}

    @Transactional(readOnly = true)
    public VoiceMemoResponseDto latest(String username, Long taskId) {
        taskAccess.requireOwned(taskId, username);
        var memo = repository.findFirstByTaskIdAndCaretakerUsernameOrderByIdDesc(taskId, username)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        return response(username, memo);
    }

    @Transactional
    public Attempt createPending(String username, Long taskId, String audioUrl) {
        uploads.deleteAudioOnRollback(audioUrl);
        taskAccess.requireOpenForUpdate(taskId, username);
        VoiceMemo memo = VoiceMemo.builder().taskId(taskId).caretakerUsername(username).audioUrl(audioUrl)
                .issueCodes("").status(VoiceMemoStatus.DRAFT).processingStatus(VoiceProcessingStatus.PROCESSING)
                .processingToken(java.util.UUID.randomUUID().toString()).processingStartedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now()).build();
        repository.save(memo);
        return attempt(memo);
    }

    @Transactional
    public Attempt beginRetry(String username, Long memoId) {
        VoiceMemo memo = ownedLocked(username, memoId);
        if (memo.getStatus() == VoiceMemoStatus.CONFIRMED || memo.getProcessingStatus() == VoiceProcessingStatus.READY
                || memo.getProcessingStatus() == VoiceProcessingStatus.INVALID_AUDIO
                || processingActive(memo)) {
            throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
        }
        taskAccess.requireOpenForUpdate(memo.getTaskId(), username);
        memo.setProcessingStatus(VoiceProcessingStatus.PROCESSING);
        memo.setProcessingStartedAt(LocalDateTime.now());
        memo.setProcessingToken(java.util.UUID.randomUUID().toString());
        memo.setProcessingError(null);
        return attempt(memo);
    }

    @Transactional
    public VoiceMemoResponseDto finish(String username, Attempt attempt, VoiceAnalysis.Result analysis) {
        VoiceMemo memo = currentAttempt(username, attempt);
        memo.setTranscript(analysis.transcript());
        memo.setSummary(analysis.summary());
        memo.setIssueCodes(String.join(",", analysis.issues().stream().map(Enum::name).toList()));
        memo.setFallbackUsed(analysis.fallbackUsed());
        memo.setProcessingStatus(analysis.fallbackUsed() ? VoiceProcessingStatus.LLM_FAILED : VoiceProcessingStatus.READY);
        memo.setProcessingError(analysis.fallbackUsed() ? ErrorCode.EXTERNAL_API_ERROR.getCode() : null);
        memo.setProcessingToken(null);
        return response(username, memo);
    }

    @Transactional
    public VoiceMemoResponseDto fail(String username, Attempt attempt, ErrorCode error) {
        VoiceMemo memo = currentAttempt(username, attempt);
        memo.setProcessingStatus(error == ErrorCode.FILE_NOT_SUPPORTED
                ? VoiceProcessingStatus.INVALID_AUDIO : memo.getTranscript() == null
                    ? VoiceProcessingStatus.STT_FAILED : VoiceProcessingStatus.LLM_FAILED);
        memo.setProcessingError(error.getCode());
        memo.setProcessingToken(null);
        return response(username, memo);
    }

    // 호출 측이 먼저 텃밭 접근 권한을 확인한다. 원문 없이 확정된 관찰 코드만 다음 날 생성에 전달한다.
    @Transactional(readOnly = true)
    public java.util.List<String> recentIssuesForTasks(java.util.List<Long> taskIds, java.time.LocalDate since) {
        if (taskIds.isEmpty()) return java.util.List.of();
        if (taskIds.size() > 500) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        return repository.findConfirmedIssues(taskIds, since.atStartOfDay()).stream()
                .flatMap(v -> java.util.Arrays.stream(v.getIssueCodes().split(",")))
                .filter(s -> !s.isBlank()).distinct().toList();
    }

    private Attempt attempt(VoiceMemo memo) {
        return new Attempt(memo.getId(), memo.getTaskId(), memo.getProcessingToken(), memo.getAudioUrl(), memo.getTranscript());
    }

    private VoiceMemo currentAttempt(String username, Attempt attempt) {
        VoiceMemo memo = ownedLocked(username, attempt.memoId());
        if (memo.getStatus() != VoiceMemoStatus.DRAFT || memo.getProcessingStatus() != VoiceProcessingStatus.PROCESSING
                || !java.util.Objects.equals(memo.getProcessingToken(), attempt.token())) {
            throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
        }
        return memo;
    }

    private VoiceMemo ownedLocked(String username, Long memoId) {
        VoiceMemoRepository.Access access = repository.findAccessById(memoId)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (username == null || !username.equals(access.getCaretakerUsername())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        taskAccess.requireOwnedForUpdate(access.getTaskId(), username);
        VoiceMemo memo = repository.findForUpdate(memoId)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!username.equals(memo.getCaretakerUsername()) || !access.getTaskId().equals(memo.getTaskId())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        return memo;
    }

    private VoiceMemoResponseDto response(String username, VoiceMemo memo) {
        boolean completed = taskAccess.requireOwned(memo.getTaskId(), username).completed();
        return VoiceMemoResponseDto.builder().voiceMemoId(IdPrefix.VOICE_MEMO.format(memo.getId()))
                .transcript(memo.getTranscript()).summary(memo.getSummary())
                .issues(memo.getIssueCodes().isEmpty() ? java.util.List.of() : java.util.List.of(memo.getIssueCodes().split(",")))
                .status(memo.getStatus()).processingStatus(memo.getProcessingStatus())
                .retryable(!completed && memo.getStatus() == VoiceMemoStatus.DRAFT
                        && (memo.getProcessingStatus() == VoiceProcessingStatus.STT_FAILED || memo.getProcessingStatus() == VoiceProcessingStatus.LLM_FAILED
                            || (memo.getProcessingStatus() == VoiceProcessingStatus.PROCESSING && !processingActive(memo))))
                .processingError(memo.getProcessingError()).build();
    }

    private boolean processingActive(VoiceMemo memo) {
        // STT 다음 LLM까지 기다리므로 한 번의 HTTP 제한 시간만으로 처리를 만료시키지 않는다.
        var window = readTimeout.multipliedBy(2).plusSeconds(30);
        if (window.compareTo(java.time.Duration.ofMinutes(2)) < 0) window = java.time.Duration.ofMinutes(2);
        return memo.getProcessingStatus() == VoiceProcessingStatus.PROCESSING && memo.getProcessingStartedAt() != null
                && memo.getProcessingStartedAt().isAfter(LocalDateTime.now().minus(window));
    }

    public record ConfirmedMemo(String voiceMemoId, String transcript, String summary, java.util.List<String> issues) {}

    /** 작업 완료 서비스가 같은 작업의 확정된 메모만 일기에 연결할 때 사용한다. */
    @Transactional(readOnly = true)
    public ConfirmedMemo getConfirmedForTask(String username, Long taskId, Long memoId) {
        taskAccess.requireOwned(taskId, username);
        VoiceMemo memo = repository.findById(memoId)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!username.equals(memo.getCaretakerUsername()) || !taskId.equals(memo.getTaskId())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        if (memo.getStatus() != VoiceMemoStatus.CONFIRMED) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        return new ConfirmedMemo(IdPrefix.VOICE_MEMO.format(memo.getId()), memo.getTranscript(), memo.getSummary(),
                memo.getIssueCodes().isEmpty() ? java.util.List.of() : java.util.List.of(memo.getIssueCodes().split(",")));
    }

    @Transactional
    public VoiceMemoConfirmResponseDto confirm(String username, Long memoId, String summary) {
        if (summary != null && (summary.isBlank() || summary.length() > 1000)) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        VoiceMemoRepository.Access initial = repository.findAccessById(memoId)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (username == null || !username.equals(initial.getCaretakerUsername())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        // 작업, 메모 순으로 잠가 재배정 및 완료 처리와 잠금 순서를 맞춘다.
        var task = taskAccess.requireOwnedForUpdate(initial.getTaskId(), username);
        VoiceMemo memo = repository.findForUpdate(memoId)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!username.equals(memo.getCaretakerUsername()) || !task.taskId().equals(memo.getTaskId())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        if (memo.getStatus() == VoiceMemoStatus.CONFIRMED) {
            if (summary != null && !summary.equals(memo.getSummary())) {
                throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
            }
        } else {
            if (task.completed()) throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
            if (memo.getProcessingStatus() == VoiceProcessingStatus.PROCESSING) throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
            if (memo.getTranscript() == null || memo.getTranscript().isBlank()) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
            if (summary != null && !summary.equals(memo.getSummary())) {
                memo.setSummary(summary);
                // 수정된 문장과 이전 자동 추출 결과가 어긋날 수 있어 후속 작업에 자동 전파하지 않는다.
                memo.setIssueCodes("");
            }
            memo.setStatus(VoiceMemoStatus.CONFIRMED);
            memo.setConfirmedAt(LocalDateTime.now());
        }
        return VoiceMemoConfirmResponseDto.builder().voiceMemoId(IdPrefix.VOICE_MEMO.format(memo.getId()))
                .status(memo.getStatus()).build();
    }
}
