package community.likelion.growwithusserver.care.data.repository;

import community.likelion.growwithusserver.care.data.entity.TaskDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface TaskDayRepository extends JpaRepository<TaskDay, Long> {
    @Modifying
    @Query(value = "insert ignore into care_task_day (caretaker_username, task_date) values (:username, :date)", nativeQuery = true)
    void ensureDay(@Param("username") String username, @Param("date") LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TaskDay d where d.caretakerUsername = :username and d.date = :date")
    Optional<TaskDay> lockDay(@Param("username") String username, @Param("date") LocalDate date);
}
