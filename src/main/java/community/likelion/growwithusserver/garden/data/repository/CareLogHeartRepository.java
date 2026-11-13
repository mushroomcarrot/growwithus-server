package community.likelion.growwithusserver.garden.data.repository;

import community.likelion.growwithusserver.garden.data.entity.CareLogHeart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CareLogHeartRepository extends JpaRepository<CareLogHeart, Long> {
    long countByUserUsername(String username);
    boolean existsByCareLogId(Long careLogId);

    // 주어진 돌봄 기록 중 하트를 받은 것의 PK
    @Query("select h.careLogId from CareLogHeart h where h.careLogId in :careLogIds")
    List<Long> findHeartedCareLogIds(Collection<Long> careLogIds);

    // 관리사가 from 이상 to 미만에 받은 하트 수
    long countByCaretakerUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String caretakerUsername,
                                                                                 LocalDateTime from, LocalDateTime to);
}
