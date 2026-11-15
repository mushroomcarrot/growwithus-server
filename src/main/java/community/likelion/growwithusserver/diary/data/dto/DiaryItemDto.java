package community.likelion.growwithusserver.diary.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import community.likelion.growwithusserver.diary.data.enums.DiaryItemType;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// 성장 일기 항목. type마다 쓰는 필드가 달라서 값이 없는 필드는 응답에서 뺀다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiaryItemDto {
    private DiaryItemType type;
    private LocalDate date;
    // 기록 시각. CARE_LOG와 STAGE_CHANGE에만 있다
    private java.time.OffsetDateTime createdAt;

    // CARE_LOG
    private String careLogId;
    private List<String> taskTypes;
    private String summary;
    private String transcript;
    private String photoUrl;
    private String caretakerName;
    private Boolean hearted;

    // STAGE_CHANGE
    private String eventId;
    private GrowthStage fromStage;
    private GrowthStage toStage;

    // REST_DAY
    private String message;
}
