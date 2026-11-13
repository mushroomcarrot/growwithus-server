package community.likelion.growwithusserver.ai.logic;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskHistoryTest {
    private final TaskRules rules = new TaskRules();
    private final LocalDate today = LocalDate.of(2026, 11, 12);
    private TaskRules.Guidance fertilizer(int interval) {
        return new TaskRules.Guidance(TaskRules.Type.FERTILIZE, Set.of(Stage.SPROUT), interval,
                "fertilizer", "follow verified instructions", 10, "verified:test");
    }

    @Test void includesWorkOlderThanFourteenDaysWhenGuidanceRequiresIt() {
        var guidance = List.of(fertilizer(30));
        var completedOn = today.minusDays(21);
        assertEquals(today.minusDays(30), rules.historyStart(today, guidance));
        assertFalse(completedOn.isBefore(rules.historyStart(today, guidance)));
        var context = new TaskRules.Context(today, Stage.SPROUT, true, false, guidance,
                List.of(new TaskRules.History(TaskRules.Type.FERTILIZE, completedOn)));
        var plan = rules.validate(context, List.of(TaskRules.Type.FERTILIZE), 120);
        assertTrue(plan.skippedTypes().contains(TaskRules.Type.FERTILIZE));
        assertEquals(TaskRules.Type.CHECK, plan.tasks().getFirst().type());
    }

    @Test void keepsBaselineWindowAndUsesLongestIntervalAcrossTypes() {
        assertEquals(today.minusDays(14), rules.historyStart(today, List.of()));
        assertEquals(today.minusDays(14), rules.historyStart(today, List.of(fertilizer(2))));
        var support = new TaskRules.Guidance(TaskRules.Type.SUPPORT, Set.of(Stage.SPROUT), 45,
                "support", "follow verified instructions", 10, "verified:test");
        assertEquals(today.minusDays(45), rules.historyStart(today, List.of(support, fertilizer(30))));
    }

    @Test void permitsWorkAtActualIntervalBoundary() {
        var context = new TaskRules.Context(today, Stage.SPROUT, true, false, List.of(fertilizer(30)),
                List.of(new TaskRules.History(TaskRules.Type.FERTILIZE, today.minusDays(30))));
        assertEquals(TaskRules.Type.FERTILIZE, rules.validate(context, List.of(TaskRules.Type.FERTILIZE), 120).tasks().getFirst().type());
    }

    @Test void boundsQueryToSupportedDatabaseDateWithoutInventingNewInterval() {
        assertEquals(LocalDate.of(1000, 1, 1), rules.historyStart(today, List.of(fertilizer(Integer.MAX_VALUE))));
    }
}
