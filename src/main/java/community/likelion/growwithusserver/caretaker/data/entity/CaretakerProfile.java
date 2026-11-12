package community.likelion.growwithusserver.caretaker.data.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "caretaker_profile")
public class CaretakerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    @Column(nullable = false, unique = true, length = 20) private String username;
    @Column(nullable = false, length = 20) private String name;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "avatar_url", nullable = false, length = 1024) private String avatarUrl;
    @Column(name = "portrait_consent", nullable = false) private boolean portraitConsent;
    @Column(name = "portrait_consented_at") private LocalDateTime portraitConsentedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
