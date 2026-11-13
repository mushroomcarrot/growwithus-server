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

import java.time.LocalDate;
import java.time.LocalDateTime;

// 텃밭에 그날 나간 퀴즈와 답. 텃밭 하나에 하루 한 문제다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "garden_quiz", uniqueConstraints = @UniqueConstraint(name = "uk_garden_quiz", columnNames = {"garden_id", "quiz_date"}))
public class GardenQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_id", nullable = false)
    private Garden garden;

    @Column(name = "quiz_date", nullable = false)
    private LocalDate quizDate;

    // 문제 은행(QuizBank)에서의 순번
    @Column(nullable = false)
    private int bankIndex;

    // 고른 보기. 아직 안 풀었으면 null
    private Integer choiceIndex;

    private Boolean correct;

    private LocalDateTime answeredAt;

    public boolean isAnswered() {
        return choiceIndex != null;
    }
}
