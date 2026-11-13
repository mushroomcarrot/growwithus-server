package community.likelion.growwithusserver.ai.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class VoiceReferenceCheckTest {
    @Test void acceptsTheActualPublicReferenceWithoutRequiringUnspokenWater() throws Exception {
        try (var stream = getClass().getResourceAsStream("/ai-evaluation/synthetic-korean-reference.txt")) {
            assertNotNull(stream);
            String reference = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(reference.contains("물"));
            assertTrue(AiPredeployCheck.matchesReference(reference, reference));
            assertTrue(AiPredeployCheck.matchesReference(reference.replace(" ", "").replace(".", "!"), reference));
        }
    }
    @Test void rejectsMissingImportantDetailsAndUnrelatedKorean() {
        String reference = "오늘 잎과 흙을 살펴봤어요. 잎에 작은 점이 보여요.";
        assertFalse(AiPredeployCheck.matchesReference("오늘 잎과 물을 살펴봤어요. 잎에 작은 점이 보여요.", reference));
        assertFalse(AiPredeployCheck.matchesReference("오늘 잎과 흙을 살펴봤어요.", reference));
        assertFalse(AiPredeployCheck.matchesReference("오늘은 날씨가 좋아서 산책을 다녀왔어요.", reference));
        assertFalse(AiPredeployCheck.matchesReference(null, reference));
    }
}
