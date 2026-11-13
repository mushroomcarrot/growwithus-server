package community.likelion.growwithusserver.garden.data.entity;

import community.likelion.growwithusserver.user.data.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 주인이 돌봄 기록에 보낸 감사 하트. 돌봄 기록 하나에 한 번만 보낼 수 있다 (care_log_id 유니크).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "care_log_heart", indexes = @Index(name = "idx_care_log_heart_caretaker", columnList = "caretaker_username, created_at"))
public class CareLogHeart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 돌봄 기록 PK (엔티티 연관 없음)
    @Column(name = "care_log_id", nullable = false, unique = true)
    private Long careLogId;

    @Column(name = "garden_id", nullable = false)
    private Long gardenId;

    // 하트를 보낸 주인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 하트를 받은 관리사의 로그인 아이디. 관리사 활동 요약의 받은 하트 수를 셀 때 쓴다
    @Column(name = "caretaker_username", nullable = false, length = 20)
    private String caretakerUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
