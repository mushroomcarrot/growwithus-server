package community.likelion.growwithusserver.notification.data.dto;

import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.user.data.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// NotificationService.send에 넘기는 알림 내용. 다른 도메인은 이 DTO만 채워서 호출한다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSendDto {
    private User receiver;
    private NotificationType type;
    private String title;
    private String body;
    // 이동할 화면 코드 (예: DT_DIARY). 없으면 null
    private String targetScreen;
    // 그 화면에서 쓸 외부 ID (예: cl_12). 없으면 null
    private String targetId;
}
