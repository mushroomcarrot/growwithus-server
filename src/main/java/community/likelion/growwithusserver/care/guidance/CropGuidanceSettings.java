package community.likelion.growwithusserver.care.guidance;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="crop_guidance_settings") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CropGuidanceSettings {
    @Id private Long cropId;
    @Lob @Column(nullable=false, columnDefinition="TEXT") private String rulesJson;
    @Column(nullable=false) private LocalDateTime updatedAt;
}
