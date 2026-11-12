package community.likelion.growwithusserver.voice.data.repository;

import community.likelion.growwithusserver.voice.data.entity.VoiceMemo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoiceMemoRepository extends JpaRepository<VoiceMemo, Long> {
    interface Access {
        Long getTaskId();
        String getCaretakerUsername();
    }

    // 잠금 전에 엔티티를 영속성 컨텍스트에 올리지 않아 오래된 확정 상태를 재사용하지 않는다.
    @Query("select v.taskId as taskId, v.caretakerUsername as caretakerUsername from VoiceMemo v where v.id = :id")
    Optional<Access> findAccessById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoiceMemo v where v.id = :id")
    Optional<VoiceMemo> findForUpdate(@Param("id") Long id);

    @Query("select v from VoiceMemo v where v.taskId in :taskIds and v.status = community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus.CONFIRMED and v.confirmedAt >= :since order by v.confirmedAt desc")
    java.util.List<VoiceMemo> findConfirmedIssues(@Param("taskIds") java.util.List<Long> taskIds,
                                                 @Param("since") java.time.LocalDateTime since);
}
