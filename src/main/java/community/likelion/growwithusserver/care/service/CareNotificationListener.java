package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class CareNotificationListener {
    private final NotificationService notifications;
    private final UserService users;
    // 동기 이벤트이므로 알림 저장 실패 시 작업 완료 트랜잭션도 롤백된다.
    @EventListener
    public void onComplete(CareCompleted event) {
        notifications.send(NotificationSendDto.builder().receiver(users.getUserEntity(event.ownerUsername()))
                .type(NotificationType.CARE_DONE).title("텃밭 돌봄을 마쳤어요").body(event.summary())
                .targetScreen("DT_DIARY").targetId(event.careLogId()).build());
    }
}
