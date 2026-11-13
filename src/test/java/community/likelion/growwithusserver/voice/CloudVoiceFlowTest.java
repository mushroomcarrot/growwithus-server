package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CloudVoiceFlowTest {
    @Test void confirmedMemoPreservesRecognizedTextSummaryAndIssues() {
        var result = new VoiceAnalysis.Result("물을 줬고 잎에 점이 있어요", "물을 줬어요. 잎에 점이 보여요.",
                List.of(VoiceAnalysis.Issue.LEAF_SPOT), false);
        assertDoesNotThrow(() -> CloudVoiceFlowCheck.confirm(result));
    }
}
