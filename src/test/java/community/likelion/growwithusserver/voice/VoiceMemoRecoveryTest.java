package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.common.response.GlobalException;
import community.likelion.growwithusserver.voice.data.enums.VoiceProcessingStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class VoiceMemoRecoveryTest {
    @Test void recoversMemoIdAfterClientMissesUploadResponseWithoutCallingModel() {
        var f = new VoiceMemoSelfTest.Fixture();
        var pending = f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/test.webm");
        var result = f.service.latest("caretaker", "tsk_1").getData();
        assertEquals("vm_1", result.getVoiceMemoId());
        assertEquals(VoiceProcessingStatus.PROCESSING, result.getProcessingStatus());
        assertFalse(result.isRetryable()); assertEquals(0, f.sttCalls); assertEquals(0, f.audioReads);
        assertEquals(pending.token(), f.rows.get(1L).getProcessingToken());
    }

    @Test void expiresAbandonedAttemptAndReusesSameMemoOnRetry() {
        var f = new VoiceMemoSelfTest.Fixture();
        var pending = f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/test.webm");
        f.rows.get(1L).setProcessingStartedAt(LocalDateTime.now().minusMinutes(3));
        assertTrue(f.service.latest("caretaker", "tsk_1").getData().isRetryable());
        assertEquals(pending.token(), f.rows.get(1L).getProcessingToken());
        var recovered = f.service.retry("caretaker", "vm_1").getData();
        assertEquals(VoiceProcessingStatus.READY, recovered.getProcessingStatus());
        assertEquals(1, f.rows.size()); assertEquals(1, f.audioReads); assertEquals(0, f.files);
    }

    @Test void returnsNewestMemoAndChecksTaskOwnership() {
        var f = new VoiceMemoSelfTest.Fixture();
        assertCode("C404", () -> f.service.latest("caretaker", "tsk_1"));
        f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/first.webm");
        f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/second.webm");
        assertEquals("vm_2", f.service.latest("caretaker", "tsk_1").getData().getVoiceMemoId());
        assertCode("C403", () -> f.service.latest("other", "tsk_1"));
        assertCode("C002", () -> f.service.latest("caretaker", "vm_1"));
    }

    @Test void completedTaskAllowsReadButNeverAdvertisesRetry() {
        var f = new VoiceMemoSelfTest.Fixture();
        f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/test.webm");
        f.rows.get(1L).setProcessingStartedAt(LocalDateTime.now().minusMinutes(3));
        f.completed = true;
        assertFalse(f.service.latest("caretaker", "tsk_1").getData().isRetryable());
        assertCode("C4095", () -> f.service.retry("caretaker", "vm_1"));
    }

    @Test void recoversConfirmationResultWithoutConfirmingAgain() {
        var f = new VoiceMemoSelfTest.Fixture();
        f.service.create("caretaker", "tsk_1", VoiceMemoSelfTest.audio());
        f.service.confirm("caretaker", "vm_1", null);
        var confirmedAt = f.rows.get(1L).getConfirmedAt();
        f.completed = true;
        var latest = f.service.latest("caretaker", "tsk_1").getData();
        assertEquals(community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus.CONFIRMED, latest.getStatus());
        assertEquals(confirmedAt, f.rows.get(1L).getConfirmedAt());
        assertFalse(latest.isRetryable()); assertEquals(1, f.sttCalls); assertEquals(1, f.files);
    }

    @Test void neverReturnsAnotherCaretakersMemoEvenIfItIsNewer() {
        var f = new VoiceMemoSelfTest.Fixture();
        f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/first.webm");
        f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/second.webm");
        f.rows.get(2L).setCaretakerUsername("other");
        assertEquals("vm_1", f.service.latest("caretaker", "tsk_1").getData().getVoiceMemoId());
    }

    @Test void finishedInferenceWinsBeforeRetryAndRemainsRecoverable() {
        var f = new VoiceMemoSelfTest.Fixture();
        var attempt = f.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/test.webm");
        f.rows.get(1L).setProcessingStartedAt(LocalDateTime.now().minusMinutes(3));
        assertTrue(f.service.latest("caretaker", "tsk_1").getData().isRetryable());
        f.store.finish("caretaker", attempt, new community.likelion.growwithusserver.ai.logic.VoiceAnalysis.Result(
                "original", "summary", java.util.List.of(), false));
        assertCode("C4095", () -> f.service.retry("caretaker", "vm_1"));
        assertEquals(VoiceProcessingStatus.READY, f.service.latest("caretaker", "tsk_1").getData().getProcessingStatus());
        assertEquals(0, f.audioReads); assertEquals(1, f.rows.size());
    }

    private void assertCode(String code, Runnable action) {
        var error = assertThrows(GlobalException.class, action::run);
        assertEquals(code, error.getErrorCode().getCode());
    }
}
