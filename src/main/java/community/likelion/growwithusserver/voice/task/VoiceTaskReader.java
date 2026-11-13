package community.likelion.growwithusserver.voice.task;

import java.util.Optional;

/** 작업 도메인이 실제 담당자와 완료 상태를 조회해 구현한다. 사용자 입력을 그대로 반환하면 안 된다. */
public interface VoiceTaskReader {
    Optional<VoiceTask> findTask(Long taskId);

    /** 현재 트랜잭션이 끝날 때까지 작업 행을 잠근다. 재배정과 작업 완료도 같은 행을 잠가야 한다. */
    Optional<VoiceTask> findTaskForUpdate(Long taskId);

    record VoiceTask(Long taskId, String caretakerUsername, boolean completed) {}
}
