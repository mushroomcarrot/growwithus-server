package community.likelion.growwithusserver.garden.care;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 홈, 일기, 하트, 수확 안내가 돌봄 기록에서 필요로 하는 값.
 *
 * @param careLogId         돌봄 기록 PK (외부 ID cl_3의 숫자)
 * @param gardenId          돌본 텃밭 PK
 * @param caretakerUsername 돌본 관리사의 로그인 아이디
 * @param taskTypes         한 작업 종류 (WATER, SUPPORT 등 명세의 task.type)
 * @param summary           관리사가 남긴 내용의 요약
 * @param photoUrl          인증 사진. 없으면 null
 * @param createdAt         작업을 끝낸 시각
 */
public record GardenCareLog(Long careLogId, Long gardenId, String caretakerUsername, List<String> taskTypes,
                            String summary, String photoUrl, LocalDateTime createdAt, String transcript) {
    public GardenCareLog(Long careLogId, Long gardenId, String caretakerUsername, List<String> taskTypes,
                         String summary, String photoUrl, LocalDateTime createdAt) {
        this(careLogId, gardenId, caretakerUsername, taskTypes, summary, photoUrl, createdAt, null);
    }
    public GardenCareLog {
        taskTypes = taskTypes == null ? List.of() : List.copyOf(taskTypes);
    }
}
