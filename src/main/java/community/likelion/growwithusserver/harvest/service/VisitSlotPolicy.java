package community.likelion.growwithusserver.harvest.service;

import community.likelion.growwithusserver.common.id.InvalidIdException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 방문 슬롯 규칙. 슬롯은 테이블에 두지 않고 이 규칙으로 계산한다.
 *
 * 1. 토요일, 일요일만 연다.
 * 2. 관리사가 맞이할 수 있는 시간은 10시, 11시, 14시, 15시에 시작하는 1시간이다 (점심 시간 제외).
 * 3. 한 농장의 한 슬롯에는 두 가족까지 예약할 수 있다.
 * 4. 관리사가 미리 알 수 있도록 내일 이후의 슬롯만 예약을 받는다.
 */
public final class VisitSlotPolicy {
    public static final List<Integer> START_HOURS = List.of(10, 11, 14, 15);
    public static final int SLOT_CAPACITY = 2;
    // from, to를 생략했을 때 내려주는 기간과, 한 번에 조회할 수 있는 최대 기간 (일)
    public static final int DEFAULT_RANGE_DAYS = 14;
    public static final int MAX_RANGE_DAYS = 31;

    private static final String ID_PREFIX = "slot_";
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private VisitSlotPolicy() {
    }

    public record Slot(LocalDate date, LocalTime startTime) {
        public LocalTime endTime() {
            return startTime.plusHours(1);
        }

        // 예: slot_20261128_10
        public String slotId() {
            return ID_PREFIX + date.format(ID_DATE) + "_" + String.format("%02d", startTime.getHour());
        }

        public String startTimeText() {
            return startTime.format(TIME);
        }

        public String endTimeText() {
            return endTime().format(TIME);
        }
    }

    // 기간 안의 주말 슬롯, 날짜와 시간 오름차순
    public static List<Slot> slotsBetween(LocalDate from, LocalDate to) {
        List<Slot> slots = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!isWeekend(date)) {
                continue;
            }
            for (int hour : START_HOURS) {
                slots.add(new Slot(date, LocalTime.of(hour, 0)));
            }
        }
        return slots;
    }

    // slotId를 슬롯으로 바꾼다. 형식이 틀리거나 규칙에 없는 날짜, 시간이면 C002
    public static Slot parse(String slotId) {
        if (slotId == null || !slotId.startsWith(ID_PREFIX)) {
            throw new InvalidIdException();
        }

        String[] parts = slotId.substring(ID_PREFIX.length()).split("_");
        if (parts.length != 2) {
            throw new InvalidIdException();
        }

        try {
            LocalDate date = LocalDate.parse(parts[0], ID_DATE);
            int hour = Integer.parseInt(parts[1]);
            if (!isWeekend(date) || !START_HOURS.contains(hour)) {
                throw new InvalidIdException();
            }
            return new Slot(date, LocalTime.of(hour, 0));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new InvalidIdException();
        }
    }

    public static boolean isBookable(Slot slot, LocalDate today) {
        return slot.date().isAfter(today);
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
