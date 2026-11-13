package community.likelion.growwithusserver.game.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 리그와 퀘스트의 한 주는 월요일에 시작해 일요일에 끝난다.
public final class GameWeek {
    private GameWeek() {
    }

    // 그 날이 속한 주의 월요일
    public static LocalDate startOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate endOf(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }
}
