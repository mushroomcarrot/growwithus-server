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
    @Query("select t from Task t where t.caretakerUsername = :username and t.date = :date and t.archiveNumber = 0 order by t.id")
    List<Task> findAllByCaretakerUsernameAndDateOrderByIdAsc(String username, LocalDate date);
    List<Task> findAllByGardenIdAndDateBetweenOrderByDateDesc(Long gardenId, LocalDate from, LocalDate to);
    @Query("select t from Task t where t.gardenId = :gardenId and t.status = :status "
            + "and t.completedAt >= :since and t.completedAt < :until order by t.completedAt desc")
    List<Task> findCompletedHistory(@Param("gardenId") Long gardenId, @Param("status") TaskStatus status,
                                    @Param("since") LocalDateTime since, @Param("until") LocalDateTime until);
    @Query("select (count(t) > 0) from Task t where t.gardenId = :gardenId and t.date = :date and t.archiveNumber = 0")
    boolean existsByGardenIdAndDate(Long gardenId, LocalDate date);
    // 같은 날 같은 텃밭에서 이미 돌봄 기록에 묶인 현재 회차 작업. 가장 최근에 끝낸 것부터
    @Query("select t from Task t where t.gardenId = :gardenId and t.caretakerUsername = :username and t.date = :date "
            + "and t.archiveNumber = 0 and t.careLogId is not null order by t.completedAt desc, t.id desc")
    List<Task> findBundled(@Param("gardenId") Long gardenId, @Param("username") String username, @Param("date") LocalDate date);
    List<Task> findAllByCareLogIdOrderByIdAsc(Long careLogId);
    // 돌봄 기록은 텃밭 방문 단위로 묶이므로 완료한 작업 수는 작업에서 센다
    @Query("select count(t) from Task t where t.caretakerUsername = :username and t.status = :status "
            + "and t.completedAt >= :since and t.completedAt < :until")
    long countCompleted(@Param("username") String username, @Param("status") TaskStatus status,
                        @Param("since") LocalDateTime since, @Param("until") LocalDateTime until);
    long countByCaretakerUsernameAndStatus(String username, TaskStatus status);
    long countByCaretakerUsernameAndDateAndStatus(String username, LocalDate date, TaskStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findForUpdate(@Param("id") Long id);
}
