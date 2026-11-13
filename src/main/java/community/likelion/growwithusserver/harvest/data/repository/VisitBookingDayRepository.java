package community.likelion.growwithusserver.harvest.data.repository;
import community.likelion.growwithusserver.harvest.data.entity.VisitBookingDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;

public interface VisitBookingDayRepository extends JpaRepository<VisitBookingDay, Long> {
    @Modifying
    @Query(value = "insert into visit_booking_day (farm_id, visit_date) values (:farmId, :date) on duplicate key update id = id", nativeQuery = true)
    void ensureDay(@Param("farmId") Long farmId, @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from VisitBookingDay d where d.farmId = :farmId and d.date = :date")
    Optional<VisitBookingDay> lockDay(@Param("farmId") Long farmId, @Param("date") LocalDate date);

    // 일반 count는 이전 스냅샷을 볼 수 있으므로 잠금 읽기로 현재 예약 ID를 센다.
    @Query(value = "select id from visit where farm_id = :farmId and visit_date = :date and start_time = :start "
            + "and status = 'RESERVED' order by id for update", nativeQuery = true)
    List<Long> reservedInSlotForUpdate(@Param("farmId") Long farmId, @Param("date") LocalDate date, @Param("start") LocalTime start);
}
