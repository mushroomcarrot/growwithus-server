package community.likelion.growwithusserver.harvest.data.repository;

import community.likelion.growwithusserver.harvest.data.entity.Visit;
import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findByIdForUpdate(@Param("id") Long id);
    List<Visit> findAllByFarmIdAndVisitDateBetweenAndStatus(Long farmId, LocalDate from, LocalDate to, VisitStatus status);

    long countByFarmIdAndVisitDateAndStartTimeAndStatus(Long farmId, LocalDate visitDate, LocalTime startTime, VisitStatus status);

    boolean existsByGardenIdAndVisitDateAndStatus(Long gardenId, LocalDate visitDate, VisitStatus status);
}
