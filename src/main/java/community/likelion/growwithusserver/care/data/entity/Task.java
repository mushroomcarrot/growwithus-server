package community.likelion.growwithusserver.care.data.entity;

import community.likelion.growwithusserver.ai.logic.TaskRules.Type;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "care_task", uniqueConstraints = @UniqueConstraint(name = "uk_task_garden_date_type",
        columnNames = {"garden_id", "task_date", "task_type"}))
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "garden_id", nullable = false) private Long gardenId;
    @Column(name = "caretaker_username", nullable = false, length = 20) private String caretakerUsername;
    @Column(name = "task_date", nullable = false) private LocalDate date;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "task_type", nullable = false, length = 24) private Type type;
    @Column(nullable = false, length = 80) private String title;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false) private int minutes;
    @Column(name = "source_id", nullable = false, length = 512) private String sourceId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16) private TaskStatus status;
    @Column(name = "care_log_id") private Long careLogId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
}
