package community.likelion.growwithusserver.ai.logic;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Calibration;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.WeatherDay;
import community.likelion.growwithusserver.ai.logic.TaskRules.Context;
import community.likelion.growwithusserver.ai.logic.TaskRules.Guidance;
import community.likelion.growwithusserver.ai.logic.TaskRules.History;
import community.likelion.growwithusserver.ai.logic.TaskRules.Type;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 외부 라이브러리나 DB가 없어도 실행할 수 있는 핵심 로직 회귀 검사. */
public class AiLogicSelfTest {
    private static int checks;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    public static void main(String[] args) {
        taskRules();
        growth();
        voice();
        System.out.println("AI logic checks passed: " + checks);
    }

    private static void taskRules() {
        TaskRules rules = new TaskRules();
        List<Guidance> guidance = List.of(rule(Type.WATER, 15), rule(Type.SUPPORT, 20), rule(Type.WEED, 10));
        Context rain = new Context(TODAY, Stage.FLOWER, true, true, guidance, List.of());
        var plan = rules.validate(rain, List.of(Type.WATER, Type.SUPPORT, Type.SUPPORT), 120);
        check(plan.tasks().size() == 1 && plan.tasks().getFirst().type() == Type.SUPPORT, "비와 중복 제거");
        check(plan.skippedTypes().contains(Type.WATER), "물 주기 생략 표시");
        check(plan.totalMinutes() == 20, "시간 합계");
        Context unknown = new Context(TODAY, Stage.FLOWER, false, false, guidance, List.of());
        check(rules.validate(unknown, List.of(Type.WATER), 120).tasks().getFirst().type() == Type.CHECK,
                "날씨 누락 시 관찰로 대체");
        Context repeated = new Context(TODAY, Stage.FLOWER, true, false, guidance,
                List.of(new History(Type.SUPPORT, TODAY.minusDays(1)), new History(Type.WATER, TODAY)));
        check(rules.validate(repeated, List.of(Type.WATER, Type.SUPPORT), 120).tasks().getFirst().type() == Type.CHECK,
                "당일 완료와 전날 비일상 작업 반복 방지");
        Context dailyWater = new Context(TODAY, Stage.FLOWER, true, false, guidance,
                List.of(new History(Type.WATER, TODAY.minusDays(1))));
        check(rules.validate(dailyWater, List.of(Type.WATER), 120).tasks().getFirst().type() == Type.WATER,
                "검토된 일상 작업 주기는 허용");
        plan = rules.validate(new Context(TODAY, Stage.FLOWER, true, false, guidance, List.of()),
                List.of(Type.WATER, Type.SUPPORT, Type.WEED), 25);
        check(plan.totalMinutes() == 25 && plan.tasks().size() == 2, "시간 예산 안에서 선택");
        check(rules.validate(rain, List.of(Type.SUPPORT), 0).tasks().isEmpty(), "남은 시간 없으면 새 작업 없음");
        check(rules.validate(rain, List.of(Type.SUPPORT), 4).tasks().isEmpty(), "관찰도 예산 초과 금지");
        check(rules.validate(new Context(TODAY, Stage.SEED, true, false, guidance, List.of()),
                List.of(Type.SUPPORT), 120).tasks().getFirst().type() == Type.CHECK, "단계 불일치 제거");
        check(rules.validate(new Context(TODAY, Stage.FLOWER, true, false,
                        List.of(rule(Type.HARVEST_PREP, 10)), List.of()), List.of(Type.HARVEST_PREP), 120)
                .tasks().getFirst().type() == Type.CHECK, "열매 전 수확 준비 방지");
        check(rules.validate(new Context(TODAY, Stage.FLOWER, true, false, List.of(), List.of()),
                List.of(Type.FERTILIZE), 120).tasks().getFirst().type() == Type.CHECK, "근거 없는 작업 차단");
        check(rules.validate(new Context(TODAY, Stage.FLOWER, true, false, List.of(),
                        List.of(new History(Type.CHECK, TODAY))), List.of(), 120).tasks().isEmpty(),
                "완료한 관찰을 재생성하지 않음");
        rejects(() -> rules.validate(rain, List.of(), 121), "120분 상한");
        rejects(() -> new Context(TODAY, Stage.FLOWER, true, false,
                List.of(rule(Type.WATER, 10), rule(Type.WATER, 15)), List.of()), "중복 재배 기준 거부");
        rejects(() -> new Context(TODAY, Stage.FLOWER, true, false, guidance,
                List.of(new History(Type.WATER, TODAY.plusDays(1)))), "미래 완료 기록 거부");
    }

    private static Guidance rule(Type type, int minutes) {
        return new Guidance(type, Set.of(Stage.FLOWER, Stage.FRUIT), 1,
                "검사 작업", "검사 설명", minutes, "test:fixture");
    }

    private static void growth() {
        GrowthCalculator calculator = new GrowthCalculator();
        // 시험 전용 기준이다. 실제 작물의 생육 기준으로 사용하지 않는다.
        Map<Stage, Double> thresholds = Map.of(Stage.SEED, 0.0, Stage.SPROUT, 10.0,
                Stage.FLOWER, 30.0, Stage.FRUIT, 70.0);
        LocalDate start = TODAY.minusDays(7);
        List<WeatherDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(new WeatherDay(start.plusDays(i), 15));
        var result = calculator.calculate(start, TODAY, 10, thresholds, days, null);
        check(result.accumulatedGdd() == 35, "적산온도 계산");
        check(result.stage() == Stage.FLOWER && result.level() == 3, "단계와 레벨");
        check(result.progress() == 0.125, "현재 단계 안의 진행률");
        check(result.expectedHarvestDate().equals(TODAY.plusDays(7)), "최근 7일 기반 단순 예측");
        check(result.missingWeatherDays() == 0, "전체 기온 존재");
        var repeated = calculator.calculate(start, TODAY, 10, thresholds, days, null);
        check(result.equals(repeated), "재실행해도 적산온도 중복 누적 없음");
        List<WeatherDay> withFuture = new ArrayList<>(days);
        withFuture.add(new WeatherDay(TODAY, 100));
        withFuture.add(new WeatherDay(TODAY.plusDays(1), 100));
        check(calculator.calculate(start, TODAY, 10, thresholds, withFuture, null).equals(result),
                "미완료 당일 기온과 미래 기온 제외");
        var missing = calculator.calculate(start, TODAY, 10, thresholds, days.subList(1, 7), null);
        check(missing.missingWeatherDays() == 1 && missing.expectedHarvestDate() == null,
                "누락 기온을 조작하지 않고 수확 예측 보류");
        var cold = calculator.calculate(start, TODAY, 10, thresholds,
                List.of(new WeatherDay(start, -5)), null);
        check(cold.accumulatedGdd() == 0 && cold.stage() == Stage.SEED, "유효기온 음수는 0");
        var calibrated = calculator.calculate(start, TODAY, 10, thresholds, days,
                new Calibration(TODAY.minusDays(2), Stage.FRUIT));
        check(calibrated.accumulatedGdd() == 80 && calibrated.stage() == Stage.FRUIT,
                "확인일 단계 기준으로 이후 기온만 누적");
        check(calibrated.nextStage() == null && calibrated.progress() == 1, "마지막 단계 경계");
        var corrected = calculator.calculate(start, TODAY, 10, thresholds, days,
                new Calibration(TODAY, Stage.FLOWER));
        check(corrected.accumulatedGdd() == 30 && corrected.stage() == Stage.FLOWER, "관리사 실제 관찰 우선");
        List<WeatherDay> duplicated = new ArrayList<>(days);
        duplicated.add(days.getFirst());
        rejects(() -> calculator.calculate(start, TODAY, 10, thresholds, duplicated, null), "중복 기온 거부");
        rejects(() -> calculator.calculate(start, TODAY, Double.NaN, thresholds, days, null), "NaN 기준온도 거부");
        rejects(() -> calculator.calculate(start, TODAY, 10, thresholds,
                List.of(new WeatherDay(start, Double.POSITIVE_INFINITY)), null), "무한 기온 거부");
        rejects(() -> calculator.calculate(start, TODAY, 10, Map.of(Stage.SEED, 0.0), days, null), "불완전 기준 거부");
        rejects(() -> calculator.calculate(start, TODAY, 10, thresholds, days,
                new Calibration(TODAY.plusDays(1), Stage.FRUIT)), "미래 보정 거부");
    }

    private static void voice() {
        var result = new VoiceAnalysis.Result("잎에 점이 있어요", "잎에 점이 보여요",
                List.of(VoiceAnalysis.Issue.LEAF_SPOT), false);
        check(result.transcript().equals("잎에 점이 있어요"), "음성 원문 보존");
        check(VoiceAnalysis.Issue.LEAF_SPOT.label().equals("잎에 점이 있어요"), "한글 코드 문구");
        rejects(() -> new VoiceAnalysis.Result("", "요약", List.of(), false), "빈 원문 거부");
        rejects(() -> new VoiceAnalysis.Result("원문", "x".repeat(1001), List.of(), false), "요약 길이 제한");
    }

    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
