package community.likelion.growwithusserver.demo;
import community.likelion.growwithusserver.ai.logic.*;
import community.likelion.growwithusserver.demo.local.LocalDemoData;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class LocalDemoDataTest {
    @Test void providesExplicitDemoGuidanceAndDeterministicSyntheticGrowth() {
        var today = LocalDate.now(); var data = new LocalDemoData(today.minusDays(21)); var garden = data.garden();
        assertEquals("soonja", garden.caretakerUsername()); assertEquals("minji", garden.ownerUsername());
        assertTrue(garden.guidance().stream().allMatch(g -> g.sourceId().startsWith("local-demo:")));
        var result = new GrowthCalculator().calculate(garden.startedOn(), today, garden.baseTemp(), garden.requiredGdd(),
                data.weather(garden.startedOn(), today.minusDays(1)), null);
        assertEquals(GrowthCalculator.Stage.FLOWER, result.stage()); assertEquals(105.0, result.accumulatedGdd());
        assertEquals(0, result.missingWeatherDays());
    }
    @Test void demoWeatherStillPassesSameRainExclusionRules() {
        var data = new LocalDemoData(LocalDate.now().minusDays(21)); var garden = data.garden();
        var context = new TaskRules.Context(LocalDate.now(), GrowthCalculator.Stage.FLOWER, true, true, garden.guidance(), java.util.List.of());
        var plan = new TaskRules().validate(context, new TaskRules().fallbackCandidates(context), 120);
        assertTrue(plan.skippedTypes().contains(TaskRules.Type.WATER));
        assertEquals(TaskRules.Type.CHECK, plan.tasks().getFirst().type());
    }
}
