package community.likelion.growwithusserver.voice.data.entity;

import community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus;
import community.likelion.growwithusserver.voice.data.enums.VoiceProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "voice_memo")
public class VoiceMemo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 실제 텃밭 도메인과 FK를 합의하기 전까지 접점에서 작업과 현재 담당자를 확인한다.
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "caretaker_username", nullable = false, length = 20)
    private String caretakerUsername;

    @Column(name = "audio_url", nullable = false, length = 1024)
    private String audioUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(length = 1000)
    private String summary;

    @Column(name = "issue_codes", nullable = false, length = 200)
    private String issueCodes;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private VoiceMemoStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "processing_status", nullable = false, length = 32)
    private VoiceProcessingStatus processingStatus;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_error", length = 16)
    private String processingError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
}
