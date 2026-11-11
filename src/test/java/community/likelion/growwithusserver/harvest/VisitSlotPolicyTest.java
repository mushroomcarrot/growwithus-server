package community.likelion.growwithusserver.harvest;

import community.likelion.growwithusserver.common.id.InvalidIdException;
import community.likelion.growwithusserver.harvest.service.VisitSlotPolicy;
import community.likelion.growwithusserver.harvest.service.VisitSlotPolicy.Slot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitSlotPolicyTest {
    // 2026-11-28은 토요일, 2026-11-29는 일요일
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 28);

    @Test
    void 주말에만_하루_네_슬롯을_만든다() {
        List<Slot> slots = VisitSlotPolicy.slotsBetween(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29));

        assertThat(slots).hasSize(8);
        assertThat(slots).extracting(Slot::date).containsOnly(SATURDAY, SATURDAY.plusDays(1));
        assertThat(slots.subList(0, 4)).extracting(Slot::slotId)
                .containsExactly("slot_20261128_10", "slot_20261128_11", "slot_20261128_14", "slot_20261128_15");
        assertThat(slots.get(0).startTimeText()).isEqualTo("10:00");
        assertThat(slots.get(0).endTimeText()).isEqualTo("11:00");
    }

    @Test
    void 평일만_있는_기간은_슬롯이_없다() {
        assertThat(VisitSlotPolicy.slotsBetween(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27))).isEmpty();
    }

    @Test
    void slotId를_슬롯으로_바꾼다() {
        Slot slot = VisitSlotPolicy.parse("slot_20261128_14");

        assertThat(slot.date()).isEqualTo(SATURDAY);
        assertThat(slot.startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(slot.endTime()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void 형식이_틀리거나_규칙에_없는_슬롯은_C002다() {
        // 평일, 열지 않는 시간, 없는 날짜, 형식 오류
        for (String slotId : new String[]{"slot_20261127_10", "slot_20261128_12", "slot_20261131_10",
                "slot_1128_10", "slot_20261128", "slot_20261128_ten", "vst_3", "", null}) {
            assertThatThrownBy(() -> VisitSlotPolicy.parse(slotId))
                    .as("slotId=%s", slotId)
                    .isInstanceOf(InvalidIdException.class);
        }
    }

    @Test
    void 내일_이후의_슬롯만_예약을_받는다() {
        Slot slot = new Slot(SATURDAY, LocalTime.of(10, 0));

        assertThat(VisitSlotPolicy.isBookable(slot, SATURDAY.minusDays(1))).isTrue();
        assertThat(VisitSlotPolicy.isBookable(slot, SATURDAY)).isFalse();
        assertThat(VisitSlotPolicy.isBookable(slot, SATURDAY.plusDays(1))).isFalse();
    }
}
