package community.likelion.growwithusserver.evolution.data.repository;

import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvolutionEventRepository extends JpaRepository<EvolutionEvent, Long> {
    @Query("select e.garden.id from EvolutionEvent e where e.id = :id")
    Optional<Long> findGardenIdById(Long id);

    List<EvolutionEvent> findAllByGardenIdAndAckedFalseOrderByIdAsc(Long gardenId);

    boolean existsByGardenIdAndAckedFalse(Long gardenId);

    // from 이상 to 미만에 생긴 이벤트 (일기의 단계 변화 카드)
    @Query("select e from EvolutionEvent e where e.garden.id = :gardenId and e.occurredAt >= :from and e.occurredAt < :to")
    List<EvolutionEvent> findByGardenBetween(Long gardenId, LocalDateTime from, LocalDateTime to);

    // 보상을 한 번만 주기 위해 확인할 때 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EvolutionEvent e where e.id = :id")
    Optional<EvolutionEvent> findByIdForUpdate(Long id);
}
