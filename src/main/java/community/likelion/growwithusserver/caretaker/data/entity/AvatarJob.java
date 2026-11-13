package community.likelion.growwithusserver.caretaker.data.entity;

import community.likelion.growwithusserver.caretaker.data.enums.AvatarStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 관리사 AI 아바타 생성 작업. 올린 사진 원본은 저장하지 않고, 만든 그림의 주소만 남긴다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "caretaker_avatar_job", indexes = @Index(name = "idx_avatar_job_username", columnList = "username, id"))
public class AvatarJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AvatarStatus status;

    // 생성에 성공했을 때만 채운다
    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
