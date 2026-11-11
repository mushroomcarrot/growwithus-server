package community.likelion.growwithusserver.harvest.data.repository;

import community.likelion.growwithusserver.harvest.data.entity.Visit;
import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    List<Visit> findAllByFarmIdAndVisitDateBetweenAndStatus(Long farmId, LocalDate from, LocalDate to, VisitStatus status);

    long countByFarmIdAndVisitDateAndStartTimeAndStatus(Long farmId, LocalDate visitDate, LocalTime startTime, VisitStatus status);

    boolean existsByGardenIdAndVisitDateAndStatus(Long gardenId, LocalDate visitDate, VisitStatus status);
}
