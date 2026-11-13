package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.voice.data.enums.VoiceProcessingStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceUnexpectedFailureTest {
    @Test void unexpectedSttFailurePreservesFileAndAllowsRetry() {
        var f = new VoiceMemoSelfTest.Fixture();
        f.stt = (bytes, name) -> { throw new IllegalStateException("worker stopped"); };
        var result = f.service.create("caretaker", "tsk_1", VoiceMemoSelfTest.audio()).getData();
        assertEquals(VoiceProcessingStatus.STT_FAILED, result.getProcessingStatus());
        assertTrue(result.isRetryable()); assertNull(result.getTranscript()); assertEquals(1, f.files);
        f.stt = (bytes, name) -> "잎을 확인했어요.";
        assertEquals(VoiceProcessingStatus.LLM_FAILED, f.service.retry("caretaker", "vm_1").getData().getProcessingStatus());
        assertEquals(1, f.rows.size());
    }
    @Test void unexpectedLlmFailureRetainsTranscriptAndRetrySkipsStt() {
        var f = new VoiceMemoSelfTest.Fixture();
        f.llm = (s,u) -> { throw new IllegalStateException("worker stopped"); };
        var first = f.service.create("caretaker", "tsk_1", VoiceMemoSelfTest.audio()).getData();
        assertEquals(VoiceProcessingStatus.LLM_FAILED, first.getProcessingStatus());
        assertNotNull(first.getTranscript()); assertTrue(first.isRetryable());
        f.llm = (s,u) -> "{\"summary\":\"잎을 확인했어요.\",\"issues\":[]}";
        var retried = f.service.retry("caretaker", "vm_1").getData();
        assertEquals(VoiceProcessingStatus.READY, retried.getProcessingStatus());
        assertEquals(first.getTranscript(), retried.getTranscript());
        assertEquals(1, f.sttCalls); assertEquals(0, f.audioReads); assertEquals(1, f.files);
    }
}
