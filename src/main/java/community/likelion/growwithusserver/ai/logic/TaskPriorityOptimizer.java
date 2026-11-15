package community.likelion.growwithusserver.ai.logic;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 안전 검사를 통과한 작업에만 적용하는 0/1 배낭 최적화. 점수는 명시적 운영 규칙이다. */
public final class TaskPriorityOptimizer {
    private record Selection(int score, List<TaskRules.Guidance> tasks) {}

    public boolean hasReportedIssue(TaskRules.Context context) {
        return Arrays.stream(VoiceAnalysis.Issue.values()).anyMatch(issue -> context.recentIssues().contains(issue.name()));
    }

    public int score(TaskRules.Context context, TaskRules.Guidance rule) {
        int base = switch (rule.type()) {
            case CHECK -> hasReportedIssue(context) ? 1000 : 10;
            case WATER -> 60;
            case HARVEST_PREP -> 55;
            case SUPPORT -> 35;
            case PRUNE -> 30;
            case WEED -> 25;
            case FERTILIZE -> 20;
        };
        var last = context.history().stream().filter(item -> item.type() == rule.type())
                .map(TaskRules.History::completedOn).max(Comparator.naturalOrder());
        if (last.isEmpty()) return base + 10;
        int interval = rule.type() == TaskRules.Type.WATER || rule.type() == TaskRules.Type.CHECK
                ? rule.minimumIntervalDays() : Math.max(2, rule.minimumIntervalDays());
        long overdue = Math.max(0, ChronoUnit.DAYS.between(last.get(), context.date()) - interval);
        return base + (int) Math.min(30, overdue / interval * 6);
    }

    public List<TaskRules.Guidance> select(TaskRules.Context context, List<TaskRules.Guidance> eligible, int budget) {
        if (budget < 0 || budget > 120) throw new IllegalArgumentException("작업 예산은 0분부터 120분입니다.");
        Selection[] best = new Selection[budget + 1];
        best[0] = new Selection(0, List.of());
        for (var rule : eligible.stream().sorted(Comparator.comparing(TaskRules.Guidance::type)).toList()) {
            int value = score(context, rule);
            for (int minute = budget; minute >= rule.minutes(); minute--) {
                Selection previous = best[minute - rule.minutes()];
                if (previous == null) continue;
                int candidate = previous.score() + value;
                if (best[minute] == null || candidate > best[minute].score()) {
                    var selected = new ArrayList<>(previous.tasks());
                    selected.add(rule);
                    best[minute] = new Selection(candidate, List.copyOf(selected));
                }
            }
        }
        Selection winner = best[0];
        for (var selection : best) {
            if (selection != null && selection.score() > winner.score()) winner = selection;
        }
        return winner.tasks().stream().sorted(Comparator
                .comparingInt((TaskRules.Guidance rule) -> score(context, rule)).reversed()
                .thenComparing(TaskRules.Guidance::type)).toList();
    }
}
