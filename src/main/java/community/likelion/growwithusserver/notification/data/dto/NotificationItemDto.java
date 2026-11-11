package community.likelion.growwithusserver.notification.data.dto;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.time.DateTimes;
import community.likelion.growwithusserver.notification.data.entity.Notification;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItemDto {
    private String notificationId;
    private NotificationType type;
    private String title;
    private String body;
    // 이동 대상이 없는 알림이면 null
    private Target target;
    private boolean read;
    private OffsetDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Target {
        private String screen;
        private String id;
    }

    public static NotificationItemDto from(Notification notification) {
        Target target = notification.getTargetScreen() == null
                ? null
                : new Target(notification.getTargetScreen(), notification.getTargetId());

        return NotificationItemDto.builder()
                .notificationId(IdPrefix.NOTIFICATION.format(notification.getId()))
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .target(target)
                .read(notification.isRead())
                .createdAt(DateTimes.toOffset(notification.getCreatedAt()))
                .build();
    }
}
