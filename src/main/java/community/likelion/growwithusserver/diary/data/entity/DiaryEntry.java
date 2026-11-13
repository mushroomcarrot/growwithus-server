package community.likelion.growwithusserver.diary.data.entity;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity
@Table(name = "diary_entry", uniqueConstraints = @UniqueConstraint(name = "uk_diary_source", columnNames = {"entry_type", "source_id"}),
        indexes = @Index(name = "idx_diary_garden_id", columnList = "garden_id,id"))
public class DiaryEntry {
    public enum Type { CARE_LOG, STAGE_CHANGE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "garden_id", nullable = false) private Long gardenId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", nullable = false, length = 24) private Type type;
    @Column(name = "source_id", nullable = false) private Long sourceId;
    @Column(name = "task_types", length = 200) private String taskTypes;
    @Lob @Column(columnDefinition = "TEXT") private String transcript;
    @Column(nullable = false, length = 1000) private String summary;
    @Column(name = "photo_url", length = 1024) private String photoUrl;
    @Column(name = "caretaker_name", length = 20) private String caretakerName;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "from_stage", length = 16) private Stage fromStage;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "to_stage", length = 16) private Stage toStage;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
