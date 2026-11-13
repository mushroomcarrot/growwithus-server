package community.likelion.growwithusserver.ai.evaluation;
import community.likelion.growwithusserver.ai.logic.VoiceAnalysis.Issue;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalAiEvaluationTest {
    private LocalAiEvaluation.Outcome item(List<Issue> expected, List<Issue> predicted, boolean fallback, long latency) {
        return new LocalAiEvaluation.Outcome("fixture", expected, predicted, fallback, latency, "summary");
    }
    @Test void calculatesMicroMetricsAndNearestRankLatency() {
        var score = LocalAiEvaluation.score(List.of(
                item(List.of(Issue.LEAF_SPOT), List.of(Issue.LEAF_SPOT), false, 30),
                item(List.of(Issue.STEM_DAMAGE), List.of(Issue.WILTING), false, 10),
                item(List.of(), List.of(), false, 20)));
        assertEquals(1, score.truePositives()); assertEquals(1, score.falsePositives()); assertEquals(1, score.falseNegatives());
        assertEquals(.5, score.precision()); assertEquals(.5, score.recall()); assertEquals(.5, score.f1());
        assertEquals(2, score.exactMatches()); assertEquals(20, score.p50Ms()); assertEquals(30, score.p95Ms());
    }
    @Test void fallbackNeverCountsAsModelSuccessEvenWithMatchingEmptyTags() {
        var score = LocalAiEvaluation.score(List.of(item(List.of(), List.of(), true, 5)));
        assertEquals(0, score.exactMatches()); assertEquals(1, score.fallbacks()); assertEquals(0, score.f1());
        assertTrue(Double.isFinite(score.precision()));
    }
    @Test void emptyEvaluationCannotLookLikePerfectAccuracy() {
        assertThrows(IllegalArgumentException.class, () -> LocalAiEvaluation.score(List.of()));
    }
    @Test void syntheticDatasetIncludesPositiveNegativeAndAdversarialExamples() throws Exception {
        var examples = List.of(LocalAiEvaluation.examples());
        assertEquals(12, examples.size());
        assertTrue(examples.stream().anyMatch(e -> e.id().equals("quoted-instruction") && e.expectedIssues().isEmpty()));
        for (var code : Issue.values()) assertTrue(examples.stream().anyMatch(e -> e.expectedIssues().contains(code)));
    }
}
