package community.likelion.growwithusserver.growth.service;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@org.springframework.context.annotation.Profile("local-demo")
@Component @RequiredArgsConstructor
public class GrowthNotificationListener {
    private final NotificationService notifications;
    private final UserService users;
    @EventListener
    public void changed(GrowthChanged event) {
        notifications.send(NotificationSendDto.builder().receiver(users.getUserEntity(event.ownerUsername()))
                .type(NotificationType.STAGE_UP).title("작물이 자랐어요").body(event.summary())
                .targetScreen("DT_EVOLVE").targetId(event.eventId()).build());
    }
}
