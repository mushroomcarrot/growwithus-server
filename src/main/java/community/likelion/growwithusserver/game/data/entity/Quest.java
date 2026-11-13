package community.likelion.growwithusserver.game.data.entity;

import community.likelion.growwithusserver.game.data.enums.QuestType;
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

// 주간 퀘스트. 한 주에 종류(QuestType)마다 하나씩 있고 모든 주인이 같은 퀘스트를 받는다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quest", uniqueConstraints = @UniqueConstraint(name = "uk_quest_week_type", columnNames = {"week_start", "type"}))
public class Quest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 그 주의 월요일
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestType type;

    @Column(nullable = false, length = 50)
    private String title;

    // 달성에 필요한 횟수
    @Column(nullable = false)
    private int goal;

    @Column(nullable = false)
    private int rewardXp;
}
