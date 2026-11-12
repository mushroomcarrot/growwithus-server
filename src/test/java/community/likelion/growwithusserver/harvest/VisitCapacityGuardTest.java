package community.likelion.growwithusserver.harvest;
import community.likelion.growwithusserver.harvest.data.entity.VisitBookingDay;
import community.likelion.growwithusserver.harvest.data.exception.VisitSlotFullException;
import community.likelion.growwithusserver.harvest.data.repository.VisitBookingDayRepository;
import community.likelion.growwithusserver.harvest.service.*;
import community.likelion.growwithusserver.support.FakeRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitCapacityGuardTest {
    private final VisitSlotPolicy.Slot slot = new VisitSlotPolicy.Slot(LocalDate.of(2026, 11, 28), LocalTime.of(10, 0));
    private final List<String> calls = new ArrayList<>();
    private List<Long> reserved = List.of();
    private boolean lockMissing;
    private final VisitBookingDayRepository days = FakeRepository.of(VisitBookingDayRepository.class, (method, args) -> {
        calls.add(method);
        assertEquals(1L, args[0]); assertEquals(slot.date(), args[1]);
        return switch (method) {
            case "ensureDay" -> null;
            case "lockDay" -> lockMissing ? Optional.empty() : Optional.of(new VisitBookingDay());
            case "reservedInSlotForUpdate" -> { assertEquals(slot.startTime(), args[2]); yield reserved; }
            default -> throw new AssertionError(method);
        };
    });
    @Test void locksEvenAnEmptyDayBeforeReadingCurrentReservations() {
        new VisitCapacityGuard(days).requireCapacity(1L, slot);
        assertEquals(List.of("ensureDay", "lockDay", "reservedInSlotForUpdate"), calls);
    }
    @Test void lastFamilyFitsButNextRequestSeesFullCapacity() {
        reserved = List.of(10L);
        var guard = new VisitCapacityGuard(days); guard.requireCapacity(1L, slot);
        reserved = List.of(10L, 11L);
        assertThrows(VisitSlotFullException.class, () -> guard.requireCapacity(1L, slot));
    }
    @Test void missingLockFailsBeforeCapacityRead() {
        lockMissing = true;
        assertThrows(IllegalStateException.class, () -> new VisitCapacityGuard(days).requireCapacity(1L, slot));
        assertEquals(List.of("ensureDay", "lockDay"), calls);
    }
}
