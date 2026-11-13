package community.likelion.growwithusserver.growth.data.entity;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "garden_growth_state")
public class GrowthState {
    @Id @Column(name = "garden_id") private Long gardenId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 16) private Stage stage;
    @Column(name = "accumulated_gdd", nullable = false) private double accumulatedGdd;
    @Column(nullable = false) private double progress;
    @Column(name = "expected_harvest_date") private LocalDate expectedHarvestDate;
    @Column(name = "calibration_on") private LocalDate calibrationOn;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "calibration_stage", length = 16) private Stage calibrationStage;
    @Column(name = "calibrated_at") private LocalDateTime calibratedAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
