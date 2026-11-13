package community.likelion.growwithusserver.garden.data.entity;

import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.user.data.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 주인이 분양받은 텃밭 한 칸과 그 칸에서 자라는 작물 캐릭터의 상태.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "garden")
public class Garden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주인 한 명에 텃밭 하나다
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false, unique = true)
    private User owner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plot_id", nullable = false, unique = true)
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id", nullable = false)
    private Crop crop;

    // 담당 관리사. 분양할 때 칸의 담당 관리사를 옮겨 온다. 배정 전이면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caretaker_user_id")
    private User caretaker;

    // 작물 캐릭터 이름 (예: 토순이)
    @Column(nullable = false, length = 20)
    private String characterName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CharacterMood mood = CharacterMood.HAPPY;

    @Column(nullable = false)
    private LocalDate startedOn;

    // 현재 성장 단계. 성장 시뮬레이션과 관리사의 단계 확인이 바꾼다
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GrowthStage stage = GrowthStage.SEED;

    // 누적 유효적산온도
    @Builder.Default
    @Column(nullable = false)
    private double accumulatedGdd = 0;

    // 성장 시뮬레이션이 예측한 수확일. 아직 계산 전이면 null
    private LocalDate expectedHarvestDate;

    // 연속 응원 일수
    @Builder.Default
    @Column(nullable = false)
    private int streakDays = 0;

    // 마지막으로 응원한 날. 한 번도 안 했으면 null
    private LocalDate lastCheeredOn;

    @Builder.Default
    @Column(nullable = false)
    private int totalXp = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
