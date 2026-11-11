package community.likelion.growwithusserver.harvest.data.entity;

import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import community.likelion.growwithusserver.user.data.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 주말 방문 예약. 텃밭과 농장은 PK만 들고 있다 (엔티티 연관은 텃밭 도메인이 생기면 검토).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "visit", indexes = @Index(name = "idx_visit_farm_date", columnList = "farm_id, visit_date"))
public class Visit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "garden_id", nullable = false)
    private Long gardenId;

    // 슬롯 정원은 농장 단위로 센다
    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    // 예약한 주인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Builder.Default
    @Column(nullable = false)
    private int headcount = 1;

    @Column(length = 200)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status;

    // 한 텃밭이 같은 날 두 번 예약하지 못하게 막는 유니크 키 (텃밭ID:날짜). 취소하면 null로 비워 다시 예약할 수 있다.
    @Column(unique = true, length = 40)
    private String reservedKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime canceledAt;

    public static String reservedKeyOf(Long gardenId, LocalDate visitDate) {
        return gardenId + ":" + visitDate;
    }
}
