package community.likelion.growwithusserver.care.data.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "care_task_photo")
public class TaskPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "task_id", nullable = false) private Long taskId;
    @Column(name = "caretaker_username", nullable = false, length = 20) private String caretakerUsername;
    @Column(nullable = false, length = 1024) private String url;
    @Column(name = "ai_matched") private Boolean aiMatched;
    @Column(name = "ai_message", length = 200) private String aiMessage;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
