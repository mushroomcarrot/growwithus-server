package community.likelion.growwithusserver.game.data.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 주인 한 명의 퀘스트 진행도. 처음 진행도가 오를 때 생긴다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_quest", uniqueConstraints = @UniqueConstraint(name = "uk_user_quest", columnNames = {"quest_id", "user_id"}))
public class UserQuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 목표(goal)까지만 센다
    @Builder.Default
    @Column(nullable = false)
    private int progress = 0;

    // 보상을 받았는지
    @Builder.Default
    @Column(nullable = false)
    private boolean claimed = false;

    private LocalDateTime claimedAt;
}
