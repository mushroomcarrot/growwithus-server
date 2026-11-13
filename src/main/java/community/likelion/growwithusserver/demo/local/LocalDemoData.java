package community.likelion.growwithusserver.demo.local;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.*;
import community.likelion.growwithusserver.ai.logic.TaskRules.*;
import community.likelion.growwithusserver.care.garden.CareGardenReader.Garden;
import java.time.LocalDate;
import java.util.*;

/** local-demo에서만 쓰는 가상 시연 자료. 실제 작물 기준 또는 기온 자료가 아니다. */
public final class LocalDemoData {
    public static final String OWNER = "minji";
    public static final String CARETAKER = "soonja";
    public static final String ADMIN = "demo_admin";
    private final LocalDate startedOn;
    public LocalDemoData(LocalDate startedOn) {
        if (startedOn == null || startedOn.isAfter(LocalDate.now())) throw new IllegalArgumentException("시연 시작일을 확인해 주세요.");
        this.startedOn = startedOn;
    }
    public Garden garden() {
        return new Garden(1L, CARETAKER, OWNER, "B14", "시연용 토마토", "민지 가족", 64, 119, Stage.SPROUT, null,
                List.of(new Guidance(Type.WATER, Set.of(Stage.SPROUT, Stage.FLOWER, Stage.FRUIT), 1,
                                "흙을 확인하고 물 주기", "흙이 말랐는지 확인하고 현장 안내에 따라 물을 주세요.", 10, "local-demo:example"),
                        new Guidance(Type.CHECK, Set.of(Stage.values()), 1,
                                "잎과 흙 살피기", "잎과 흙을 살펴보고 눈에 띄는 변화를 말로 남겨 주세요.", 5, "local-demo:observation")),
                startedOn, 10, Map.of(Stage.SEED, 0.0, Stage.SPROUT, 20.0, Stage.FLOWER, 100.0, Stage.FRUIT, 250.0));
    }
    public List<WeatherDay> weather(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) return List.of();
        return from.datesUntil(to.plusDays(1)).map(date -> new WeatherDay(date, 15.0)).toList();
    }
}
