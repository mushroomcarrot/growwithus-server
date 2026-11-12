package community.likelion.growwithusserver.care.data.repository;
import community.likelion.growwithusserver.care.data.entity.CareLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface CareLogRepository extends JpaRepository<CareLog, Long> {
    List<CareLog> findAllByCaretakerUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String username,
            LocalDateTime from, LocalDateTime to);
    long countByCaretakerUsername(String username);
}
