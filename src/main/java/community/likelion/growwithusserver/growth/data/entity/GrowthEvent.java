package community.likelion.growwithusserver.growth.data.entity;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "growth_stage_event")
public class GrowthEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "garden_id", nullable = false) private Long gardenId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "from_stage", nullable = false, length = 16) private Stage fromStage;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "to_stage", nullable = false, length = 16) private Stage toStage;
    @Column(nullable = false, length = 300) private String summary;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
