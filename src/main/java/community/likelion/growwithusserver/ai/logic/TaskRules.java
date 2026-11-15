package community.likelion.growwithusserver.ai.logic;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** LLM은 작업 종류만 고른다. 실행 문구와 시간은 검토된 재배 기준에서 가져온다. */
public final class TaskRules {
    public enum Type { WATER, SUPPORT, WEED, PRUNE, FERTILIZE, HARVEST_PREP, CHECK }
    public record Guidance(Type type, Set<Stage> stages, int minimumIntervalDays,
                           String title, String description, int minutes, String sourceId) {
        public Guidance {
            Objects.requireNonNull(type);
            stages = Set.copyOf(stages);
            if (stages.isEmpty() || minimumIntervalDays < 1 || minutes < 1 || minutes > 120
                    || title == null || title.isBlank() || title.length() > 80
                    || description == null || description.isBlank() || description.length() > 500
                    || sourceId == null || sourceId.isBlank() || sourceId.length() > 512) {
                throw new IllegalArgumentException("재배 기준이 유효하지 않습니다.");
            }
        }
    }
    public record History(Type type, LocalDate completedOn) {}
    public record Context(LocalDate date, Stage stage, boolean weatherKnown, boolean rainExpected,
                          List<Guidance> guidance, List<History> history, List<String> recentIssues) {
        public Context(LocalDate date, Stage stage, boolean weatherKnown, boolean rainExpected,
                       List<Guidance> guidance, List<History> history) {
            this(date, stage, weatherKnown, rainExpected, guidance, history, List.of());
        }
        public Context {
            Objects.requireNonNull(date);
            Objects.requireNonNull(stage);
            guidance = List.copyOf(guidance);
            history = List.copyOf(history);
            recentIssues = List.copyOf(recentIssues);
            Set<Type> types = new HashSet<>();
            for (Guidance rule : guidance) {
                if (!types.add(rule.type())) throw new IllegalArgumentException("작업 기준이 중복되었습니다.");
            }
            for (History item : history) {
                if (item.type() == null || item.completedOn() == null || item.completedOn().isAfter(date)) {
                    throw new IllegalArgumentException("지난 작업 기록이 유효하지 않습니다.");
                }
            }
        }
    }
    public record Task(Type type, String title, String description, int minutes, String sourceId) {}
    public record Plan(List<Task> tasks, Set<Type> skippedTypes, int totalMinutes, String weatherNotice) {}

    public Plan validate(Context context, List<Type> candidates, int availableMinutes) {
        return validate(context, candidates, availableMinutes, true);
    }

    public Plan validateExisting(Context context, List<Type> candidates) {
        return validate(context, candidates, 120, false);
    }

    private Plan validate(Context context, List<Type> candidates, int availableMinutes, boolean addObservation) {
        Objects.requireNonNull(context);
        if (availableMinutes < 0 || availableMinutes > 120) {
            throw new IllegalArgumentException("하루 남은 작업 시간은 0분부터 120분입니다.");
        }
        List<Task> tasks = new ArrayList<>();
        List<Guidance> eligible = new ArrayList<>();
        var optimizer = new TaskPriorityOptimizer();
        boolean reportedIssue = optimizer.hasReportedIssue(context);
        var requested = new ArrayList<>(Objects.requireNonNull(candidates));
        if (addObservation && reportedIssue && !requested.contains(Type.CHECK)) requested.add(Type.CHECK);
        Set<Type> skipped = new LinkedHashSet<>();
        if (context.rainExpected() || !context.weatherKnown()) skipped.add(Type.WATER);
        Set<Type> seen = new HashSet<>();
        int total = 0;
        for (Type type : requested) {
            if (type == null || !seen.add(type)) continue;
            Guidance rule = context.guidance().stream().filter(r -> r.type() == type).findFirst().orElse(null);
            if (rule == null && type == Type.CHECK && reportedIssue) {
                rule = new Guidance(Type.CHECK, Set.of(context.stage()), 1, "최근 이상 징후 확인하기",
                        "최근 기록에 남긴 작물 변화를 먼저 살펴보고 현재 상태를 말로 남겨 주세요.", 5, "rule:reported-issue-observation");
            }
            if (rule == null || !rule.stages().contains(context.stage())
                    || (type == Type.HARVEST_PREP && context.stage() != Stage.FRUIT) || skipped.contains(type)
                    || repeated(context, rule)) {
                skipped.add(type);
                continue;
            }
            eligible.add(rule);
        }
        var selected = optimizer.select(context, eligible, availableMinutes);
        for (var rule : eligible) if (!selected.contains(rule)) skipped.add(rule.type());
        for (var rule : selected) {
            tasks.add(new Task(rule.type(), rule.title(), rule.description(), rule.minutes(), rule.sourceId()));
            total += rule.minutes();
        }
        // 재배 근거가 없거나 모델이 부적절한 작업만 고르면 관찰만 안내한다.
        boolean checkedToday = context.history().stream()
                .anyMatch(h -> h.type() == Type.CHECK && h.completedOn().equals(context.date()));
        if (tasks.isEmpty() && availableMinutes >= 5 && !checkedToday) {
            tasks.add(new Task(Type.CHECK, "텃밭 상태 확인하기",
                    "작물 상태를 살펴보고 눈에 띄는 변화가 있으면 말로 남겨 주세요.", 5, "rule:observation"));
            skipped.remove(Type.CHECK);
            total = 5;
        }
        String notice = context.rainExpected() ? "비 예보가 있어 물 주기는 제외했어요."
                : !context.weatherKnown() ? "날씨 정보가 없어 물 주기는 직접 확인해 주세요."
                : "오늘의 날씨와 최근 작업 기록을 참고했어요.";
        return new Plan(List.copyOf(tasks), Set.copyOf(skipped), total, notice);
    }

    public List<Type> fallbackCandidates(Context context) {
        return context.guidance().stream().map(Guidance::type).toList();
    }

    public LocalDate historyStart(LocalDate date, List<Guidance> guidance) {
        long days = Math.max(14, guidance.stream().mapToLong(Guidance::minimumIntervalDays).max().orElse(14));
        LocalDate start = date.minusDays(days);
        LocalDate databaseMinimum = LocalDate.of(1000, 1, 1);
        return start.isBefore(databaseMinimum) ? databaseMinimum : start;
    }

    public boolean needsObservation(Context context, List<Type> pendingTypes, boolean alreadyHasCheck) {
        if (alreadyHasCheck || pendingTypes.isEmpty()) return false;
        var available = validate(context, pendingTypes, 120);
        if (new TaskPriorityOptimizer().hasReportedIssue(context)) {
            return available.tasks().stream().anyMatch(t -> t.type() == Type.CHECK);
        }
        return available.tasks().stream().anyMatch(t -> t.type() == Type.CHECK)
                && available.tasks().stream().noneMatch(t -> pendingTypes.contains(t.type()));
    }

    private boolean repeated(Context context, Guidance rule) {
        int interval = rule.type() == Type.WATER || rule.type() == Type.CHECK
                ? rule.minimumIntervalDays() : Math.max(2, rule.minimumIntervalDays());
        return context.history().stream().anyMatch(h -> h.type() == rule.type()
                && ChronoUnit.DAYS.between(h.completedOn(), context.date()) < interval);
    }
}
