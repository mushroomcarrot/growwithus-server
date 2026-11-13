package community.likelion.growwithusserver.game.data.entity;

import community.likelion.growwithusserver.farm.data.entity.Farm;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 이웃 텃밭 주간 리그. 같은 농장의 텃밭끼리 한 주(월~일)에 하나다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "league", uniqueConstraints = @UniqueConstraint(name = "uk_league_farm_week", columnNames = {"farm_id", "week_start"}))
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    // 그 주의 월요일
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    // 그 주의 일요일
    @Column(nullable = false)
    private LocalDate weekEnd;

    // 정산이 끝났는지. 정산하면 순위가 league_member에 남는다
    @Builder.Default
    @Column(nullable = false)
    private boolean settled = false;

    private LocalDateTime settledAt;
}
