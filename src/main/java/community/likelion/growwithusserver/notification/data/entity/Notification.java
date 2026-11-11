package community.likelion.growwithusserver.notification.data.entity;

import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.user.data.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_user", columnList = "user_id, id"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수신자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String body;

    // 알림을 눌렀을 때 이동할 화면 코드 (예: DT_DIARY)와 그 화면에서 쓸 외부 ID (예: cl_12). 둘 다 없을 수 있다.
    @Column(length = 30)
    private String targetScreen;

    @Column(length = 50)
    private String targetId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 읽은 시각. null이면 안 읽은 알림이다.
    @Column
    private LocalDateTime readAt;

    public boolean isRead() {
        return readAt != null;
    }
}
