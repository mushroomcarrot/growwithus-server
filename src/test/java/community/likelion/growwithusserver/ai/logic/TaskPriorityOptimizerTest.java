package community.likelion.growwithusserver.ai.logic;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskPriorityOptimizerTest {
    private final TaskRules rules = new TaskRules();
    private final LocalDate date = LocalDate.of(2026, 11, 15);
    private TaskRules.Guidance guidance(TaskRules.Type type, int minutes) {
        return new TaskRules.Guidance(type, Set.of(GrowthCalculator.Stage.FRUIT), 2,
                type.name(), "검토된 작업", minutes, "verified:test");
    }
    private TaskRules.Context context(List<TaskRules.Guidance> guidance, List<TaskRules.History> history, List<String> issues) {
        return new TaskRules.Context(date, GrowthCalculator.Stage.FRUIT, true, false, guidance, history, issues);
    }

    @Test void optimizesCombinationInsteadOfTakingFirstExpensiveTask() {
        var guidance = List.of(guidance(TaskRules.Type.FERTILIZE, 20), guidance(TaskRules.Type.WATER, 10), guidance(TaskRules.Type.SUPPORT, 10));
        var plan = rules.validate(context(guidance, List.of(), List.of()), guidance.stream().map(TaskRules.Guidance::type).toList(), 20);
        assertEquals(List.of(TaskRules.Type.WATER, TaskRules.Type.SUPPORT), plan.tasks().stream().map(TaskRules.Task::type).toList());
        assertEquals(20, plan.totalMinutes());
        assertTrue(plan.skippedTypes().contains(TaskRules.Type.FERTILIZE));
    }

    @Test void confirmedIssueObservationOutranksRoutineWorkEvenWhenModelOmitsCheck() {
        var context = context(List.of(guidance(TaskRules.Type.WATER, 10)), List.of(), List.of("LEAF_SPOT"));
        var plan = rules.validate(context, List.of(TaskRules.Type.WATER), 10);
        assertEquals(TaskRules.Type.CHECK, plan.tasks().getFirst().type());
        assertEquals(5, plan.totalMinutes());
        assertEquals("rule:reported-issue-observation", plan.tasks().getFirst().sourceId());
        assertTrue(rules.needsObservation(context, List.of(TaskRules.Type.WATER), false));
        assertFalse(rules.needsObservation(context, List.of(TaskRules.Type.WATER), true));
    }

    @Test void neverRepeatsCompletedCheckOrWaterAndIgnoresUnknownIssueCodes() {
        var guidance = List.of(guidance(TaskRules.Type.WATER, 10));
        var history = List.of(new TaskRules.History(TaskRules.Type.CHECK, date), new TaskRules.History(TaskRules.Type.WATER, date));
        assertTrue(rules.validate(context(guidance, history, List.of("WILTING")), List.of(TaskRules.Type.WATER), 120).tasks().isEmpty());
        assertEquals(TaskRules.Type.WATER, rules.validate(context(guidance, List.of(), List.of("invented-diagnosis")), List.of(TaskRules.Type.WATER), 120).tasks().getFirst().type());
    }

    @Test void preservesWeatherStageAndMinimumIntervalGates() {
        var guidance = List.of(guidance(TaskRules.Type.WATER, 10), guidance(TaskRules.Type.FERTILIZE, 10));
        var context = new TaskRules.Context(date, GrowthCalculator.Stage.FRUIT, true, true, guidance,
                List.of(new TaskRules.History(TaskRules.Type.FERTILIZE, date.minusDays(1))), List.of("PEST_SUSPECTED"));
        var plan = rules.validate(context, List.of(TaskRules.Type.WATER, TaskRules.Type.FERTILIZE), 120);
        assertEquals(List.of(TaskRules.Type.CHECK), plan.tasks().stream().map(TaskRules.Task::type).toList());
        assertTrue(plan.skippedTypes().containsAll(List.of(TaskRules.Type.WATER, TaskRules.Type.FERTILIZE)));
        assertTrue(rules.validate(context, List.of(TaskRules.Type.WATER), 4).tasks().isEmpty());
    }

    @Test void olderDueTaskWinsEqualCostAndCandidateOrderDoesNotMatter() {
        var guidance = List.of(guidance(TaskRules.Type.WEED, 10), guidance(TaskRules.Type.PRUNE, 10));
        var context = context(guidance, List.of(new TaskRules.History(TaskRules.Type.WEED, date.minusDays(20)),
                new TaskRules.History(TaskRules.Type.PRUNE, date.minusDays(2))), List.of());
        var first = rules.validate(context, List.of(TaskRules.Type.PRUNE, TaskRules.Type.WEED), 10);
        var second = rules.validate(context, List.of(TaskRules.Type.WEED, TaskRules.Type.PRUNE), 10);
        assertEquals(TaskRules.Type.WEED, first.tasks().getFirst().type());
        assertEquals(first.tasks(), second.tasks());
    }

    @Test void dynamicProgrammingMatchesExhaustiveOptimumAcrossBudgets() {
        var eligible = Arrays.stream(TaskRules.Type.values()).map(type -> guidance(type, 4 + type.ordinal() * 3)).toList();
        var context = context(eligible, List.of(), List.of("STEM_DAMAGE"));
        var optimizer = new TaskPriorityOptimizer();
        for (int budget = 0; budget <= 120; budget++) {
            int optimum = 0;
            for (int mask = 0; mask < (1 << eligible.size()); mask++) {
                int minutes = 0, score = 0;
                for (int i = 0; i < eligible.size(); i++) if ((mask & (1 << i)) != 0) {
                    minutes += eligible.get(i).minutes(); score += optimizer.score(context, eligible.get(i));
                }
                if (minutes <= budget) optimum = Math.max(optimum, score);
            }
            var selected = optimizer.select(context, eligible, budget);
            assertTrue(selected.stream().mapToInt(TaskRules.Guidance::minutes).sum() <= budget);
            assertEquals(optimum, selected.stream().mapToInt(rule -> optimizer.score(context, rule)).sum());
            assertEquals(selected.size(), selected.stream().map(TaskRules.Guidance::type).distinct().count());
        }
    }
}
