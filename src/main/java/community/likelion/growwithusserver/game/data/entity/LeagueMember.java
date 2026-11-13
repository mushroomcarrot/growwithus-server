package community.likelion.growwithusserver.game.data.entity;

import community.likelion.growwithusserver.garden.data.entity.Garden;
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

// 정산이 끝난 리그의 최종 순위. 진행 중인 주의 순위는 XP 지급 기록(xp_history)에서 바로 계산한다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "league_member", uniqueConstraints = @UniqueConstraint(name = "uk_league_member", columnNames = {"league_id", "garden_id"}))
public class LeagueMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_id", nullable = false)
    private Garden garden;

    // 그 주에 얻은 XP
    @Column(nullable = false)
    private int weeklyXp;

    // 최종 순위. 주간 XP가 같으면 같은 순위다
    @Column(name = "final_rank", nullable = false)
    private int rank;
}
