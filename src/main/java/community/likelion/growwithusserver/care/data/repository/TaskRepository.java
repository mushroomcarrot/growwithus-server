package community.likelion.growwithusserver.care.data.repository;

import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByCaretakerUsernameAndDateOrderByIdAsc(String username, LocalDate date);
    List<Task> findAllByGardenIdAndDateBetweenOrderByDateDesc(Long gardenId, LocalDate from, LocalDate to);
    @Query("select t from Task t where t.gardenId = :gardenId and t.status = :status "
            + "and t.completedAt >= :since and t.completedAt < :until order by t.completedAt desc")
    List<Task> findCompletedHistory(@Param("gardenId") Long gardenId, @Param("status") TaskStatus status,
                                    @Param("since") LocalDateTime since, @Param("until") LocalDateTime until);
    boolean existsByGardenIdAndDate(Long gardenId, LocalDate date);
    long countByCaretakerUsernameAndDateAndStatus(String username, LocalDate date, TaskStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findForUpdate(@Param("id") Long id);
}
