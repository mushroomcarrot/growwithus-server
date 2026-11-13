package community.likelion.growwithusserver.evolution.data.entity;

import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// 진화 이벤트 (명세의 GrowthEvent). 작물의 단계가 오를 때 하나씩 생기고, 주인이 확인하면 보상을 받는다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "evolution_event", indexes = @Index(name = "idx_evolution_event_garden", columnList = "garden_id, acked"))
public class EvolutionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_id", nullable = false)
    private Garden garden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GrowthStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GrowthStage toStage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    // 확인하면 받는 XP
    @Column(nullable = false)
    private int rewardXp;

    // 확인하면 받는 배지 (BadgeCode의 이름). 없으면 null
    @Column(length = 30)
    private String rewardBadgeCode;

    // 주인이 확인했는지. 확인하면 보상을 지급하고 true로 바꾼다
    @Builder.Default
    @Column(nullable = false)
    private boolean acked = false;

    private LocalDateTime ackedAt;
}
