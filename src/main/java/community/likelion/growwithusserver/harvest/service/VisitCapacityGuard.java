package community.likelion.growwithusserver.harvest.service;
import community.likelion.growwithusserver.harvest.data.repository.VisitBookingDayRepository;
import community.likelion.growwithusserver.harvest.data.exception.VisitSlotFullException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class VisitCapacityGuard {
    private final VisitBookingDayRepository days;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCapacity(Long farmId, VisitSlotPolicy.Slot slot) {
        days.ensureDay(farmId, slot.date());
        days.lockDay(farmId, slot.date()).orElseThrow(() -> new IllegalStateException("예약 잠금 행이 없어요."));
        if (days.reservedInSlotForUpdate(farmId, slot.date(), slot.startTime()).size() >= VisitSlotPolicy.SLOT_CAPACITY) {
            throw new VisitSlotFullException();
        }
        // 잠금은 실제 예약과 알림을 저장하는 호출자 트랜잭션이 끝날 때 해제한다.
    }
}
