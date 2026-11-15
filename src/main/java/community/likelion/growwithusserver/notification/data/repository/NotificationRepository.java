package community.likelion.growwithusserver.notification.data.repository;

import community.likelion.growwithusserver.notification.data.entity.Notification;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.user.data.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 최신순. cursorId보다 오래된 알림부터 조회한다.
    @Query("select n from Notification n where n.user = :user and (:cursorId is null or n.id < :cursorId) order by n.id desc")
    List<Notification> findPage(@Param("user") User user, @Param("cursorId") Long cursorId, Pageable pageable);

    Optional<Notification> findByIdAndUser(Long id, User user);

    long countByUserAndReadAtIsNull(User user);

    // 같은 종류의 알림을 그 시각 이후에 이미 보냈는지 (하루 한 번만 보내는 알림용)
    boolean existsByUserAndTypeAndCreatedAtGreaterThanEqual(User user, NotificationType type, LocalDateTime since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.user = :user and n.readAt is null")
    int markAllRead(@Param("user") User user, @Param("readAt") LocalDateTime readAt);
}
