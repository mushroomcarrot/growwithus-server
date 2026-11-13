package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.League;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, Long> {
    Optional<League> findByFarmIdAndWeekStart(Long farmId, LocalDate weekStart);

    // 정산용. 같은 리그를 두 번 정산하지 않게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from League l where l.id = :id")
    Optional<League> findByIdForUpdate(Long id);
}
