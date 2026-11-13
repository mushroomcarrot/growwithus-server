package community.likelion.growwithusserver.ai.logic;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 계산만 수행한다. 저장, 소유권 확인, 단계 변경 이벤트 발행은 호출한 서비스가 담당한다. */
public final class GrowthCalculator {
    public enum Stage { SEED, SPROUT, FLOWER, FRUIT }

    public record WeatherDay(LocalDate date, double avgTemp) {}
    public record Calibration(LocalDate date, Stage observed) {}
    public record DailyGdd(LocalDate date, double avgTemp, double gdd) {}
    public record Result(Stage stage, int level, double accumulatedGdd, Stage nextStage,
                         Double nextStageGdd, double progress, LocalDate expectedHarvestDate,
                         LocalDate calibratedOn, List<DailyGdd> dailyGdd, long missingWeatherDays) {}

    public Result calculate(LocalDate startedOn, LocalDate asOf, double baseTemp,
                            Map<Stage, Double> requiredGdd, List<WeatherDay> weather,
                            Calibration calibration) {
        Objects.requireNonNull(startedOn);
        Objects.requireNonNull(asOf);
        if (startedOn.isAfter(asOf) || !Double.isFinite(baseTemp)) {
            throw new IllegalArgumentException("날짜 또는 기준온도가 유효하지 않습니다.");
        }
        Map<Stage, Double> thresholds = thresholds(requiredGdd);
        if (calibration != null && (calibration.date() == null || calibration.observed() == null
                || calibration.date().isBefore(startedOn) || calibration.date().isAfter(asOf)
                || calibration.observed() == Stage.SEED || calibration.observed() == Stage.SPROUT)) {
            throw new IllegalArgumentException("보정 날짜와 확인 단계가 유효하지 않습니다.");
        }

        TreeMap<LocalDate, WeatherDay> days = new TreeMap<>();
        for (WeatherDay day : Objects.requireNonNull(weather)) {
            if (day == null || day.date() == null || !Double.isFinite(day.avgTemp())) {
                throw new IllegalArgumentException("일별 기온이 유효하지 않습니다.");
            }
            // 오늘 집계는 아직 하루 전체의 평균이 아니므로 다음 날부터 반영한다.
            if (!day.date().isBefore(startedOn) && day.date().isBefore(asOf)) {
                if (days.putIfAbsent(day.date(), day) != null) {
                    throw new IllegalArgumentException("같은 날짜의 기온이 중복되었습니다.");
                }
            }
        }

        LocalDate accumulationStart = calibration == null ? startedOn : calibration.date();
        double accumulated = calibration == null ? 0 : thresholds.get(calibration.observed());
        long available = 0;
        List<DailyGdd> daily = new ArrayList<>();
        for (WeatherDay day : days.values()) {
            double gdd = Math.max(0, day.avgTemp() - baseTemp);
            if (!day.date().isBefore(accumulationStart)) {
                accumulated += gdd;
                available++;
            }
            if (!day.date().isBefore(asOf.minusDays(14))) {
                daily.add(new DailyGdd(day.date(), day.avgTemp(), gdd));
            }
        }
        if (!Double.isFinite(accumulated)) {
            throw new IllegalArgumentException("적산온도 합계가 유효하지 않습니다.");
        }
        long missing = ChronoUnit.DAYS.between(accumulationStart, asOf) - available;
        Stage stage = Stage.SEED;
        for (Stage candidate : Stage.values()) {
            if (accumulated >= thresholds.get(candidate)) stage = candidate;
        }
        Stage next = stage == Stage.FRUIT ? null : Stage.values()[stage.ordinal() + 1];
        double progress = next == null ? 1 : (accumulated - thresholds.get(stage))
                / (thresholds.get(next) - thresholds.get(stage));

        LocalDate harvestDate = stage == Stage.FRUIT ? asOf : null;
        // shortcut: 최근 7일 기온이 유지된다는 단순 가정. 실제 생육 기록 확보 후 예측 오차를 평가한다.
        if (next != null && missing == 0 && !asOf.minusDays(7).isBefore(startedOn)) {
            double recentGdd = 0;
            boolean complete = true;
            for (int i = 1; i <= 7; i++) {
                WeatherDay day = days.get(asOf.minusDays(i));
                if (day == null) { complete = false; break; }
                recentGdd += Math.max(0, day.avgTemp() - baseTemp);
            }
            if (complete && recentGdd > 0) {
                double remaining = Math.ceil((thresholds.get(Stage.FRUIT) - accumulated) / (recentGdd / 7));
                if (remaining <= 3650) harvestDate = asOf.plusDays((long) remaining);
            }
        }
        return new Result(stage, stage.ordinal() + 1, accumulated, next,
                next == null ? null : thresholds.get(next), progress, harvestDate,
                calibration == null ? null : calibration.date(), List.copyOf(daily), missing);
    }

    private Map<Stage, Double> thresholds(Map<Stage, Double> input) {
        Objects.requireNonNull(input);
        Map<Stage, Double> result = new EnumMap<>(Stage.class);
        double previous = -1;
        for (Stage stage : Stage.values()) {
            Double value = input.get(stage);
            if (value == null || !Double.isFinite(value) || value <= previous
                    || (stage == Stage.SEED && value != 0)) {
                throw new IllegalArgumentException("단계별 기준은 SEED 0부터 오름차순이어야 합니다.");
            }
            result.put(stage, value);
            previous = value;
        }
        return result;
    }
}
