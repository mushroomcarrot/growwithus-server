package community.likelion.growwithusserver.care.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "care_log")
public class CareLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "garden_id", nullable = false) private Long gardenId;
    @Column(name = "task_id", nullable = false, unique = true) private Long taskId;
    @Column(name = "caretaker_username", nullable = false, length = 20) private String caretakerUsername;
    @Column(name = "owner_username", nullable = false, length = 20) private String ownerUsername;
    @Column(name = "photo_url", length = 1024) private String photoUrl;
    @Column(name = "voice_memo_id") private Long voiceMemoId;
    @Lob @Column(columnDefinition = "TEXT") private String transcript;
    @Column(nullable = false, length = 1000) private String summary;
    @Column(name = "hearts_received", nullable = false) private long heartsReceived;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
