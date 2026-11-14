package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.ai.service.VoiceMemoAnalysisService;
import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FamilyVoiceSummaryTest {
    @Test void promptDefinesGardenCaretakerFamilyAndPreservesSpeakerAndTense() {
        var prompt = new AtomicReference<String>();
        String transcript = "어제 동료가 물을 줬고 저는 오늘 잎만 봤어요. 내일 물을 줄 거예요.";
        String summary = "어제는 동료가 물을 줬어요. 저는 오늘 잎만 확인했고, 내일 물을 줄 예정이에요.";
        var service = new VoiceMemoAnalysisService((bytes, name) -> transcript, (system, user) -> {
            prompt.set(system);
            assertTrue(user.contains(transcript));
            return new ObjectMapper().writeValueAsString(java.util.Map.of("summary", summary, "issues", java.util.List.of()));
        }, new ObjectMapper());
        var result = service.analyze(transcript);
        assertEquals(transcript, result.transcript()); assertEquals(summary, result.summary()); assertFalse(result.fallbackUsed());
        assertTrue(prompt.get().contains("화자는 텃밭을 돌보는 관리사"));
        assertTrue(prompt.get().contains("수신자는 가족"));
        assertTrue(prompt.get().contains("원문의 주체와 시제를 유지"));
    }

    @Test void friendlyToneStillRequiresExactEvidenceAndPreservesOriginalOnFailure() {
        var service = new VoiceMemoAnalysisService((bytes, name) -> "unused", (system, user) -> """
                {"summary":"오늘 물을 줬어요. 잎에 점이 보여요.","issues":[{"code":"LEAF_SPOT","evidence":"잎에 점"}]}
                """, new ObjectMapper());
        var valid = service.analyze("오늘 물 줬고 잎에 점이 보여요");
        assertFalse(valid.fallbackUsed()); assertEquals(java.util.List.of(VoiceAnalysis.Issue.LEAF_SPOT), valid.issues());
        String negative = "물은 아직 안 줬고 잎도 깨끗해요";
        var invalid = service.analyze(negative);
        assertTrue(invalid.fallbackUsed()); assertEquals(negative, invalid.transcript());
        assertEquals(negative, invalid.summary()); assertTrue(invalid.issues().isEmpty());
    }
}
