package community.likelion.growwithusserver.notification.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.notification.data.dto.NotificationItemDto;
import community.likelion.growwithusserver.notification.data.dto.NotificationListResponseDto;
import community.likelion.growwithusserver.notification.data.dto.NotificationReadResponseDto;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.entity.Notification;
import community.likelion.growwithusserver.notification.data.exception.NotificationNotFoundException;
import community.likelion.growwithusserver.notification.data.repository.NotificationRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    // PATCH /notifications/{notificationId}/read의 notificationId 자리에 오면 모두 읽음 처리한다
    public static final String ALL = "all";

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    /**
     * 알림을 저장한다. 알림이 필요한 도메인은 이 메서드만 호출한다.
     * 호출한 쪽의 트랜잭션에 참여해서 그 작업이 롤백되면 알림도 남지 않는다.
     * 프론트가 웹이라 기기 푸시는 보내지 않는다. 화면은 GET /notifications로 알림을 가져간다.
     */
    @Transactional
    public Notification send(NotificationSendDto notificationSendDto) {
        User receiver = notificationSendDto.getReceiver();

        Notification notification = notificationRepository.save(Notification.builder()
                .user(receiver)
                .type(notificationSendDto.getType())
                .title(notificationSendDto.getTitle())
                .body(notificationSendDto.getBody())
                .targetScreen(notificationSendDto.getTargetScreen())
                .targetId(notificationSendDto.getTargetId())
                .createdAt(LocalDateTime.now())
                .build());
        log.info("[send] 알림 저장: {} ({})", receiver.getUsername(), notification.getType());

        return notification;
    }

    @Transactional(readOnly = true)
    public GlobalResponse<NotificationListResponseDto> getNotifications(String username, CursorPageRequest pageRequest) {
        User user = userService.getUserEntity(username);

        Long cursorId = pageRequest.getCursorId(IdPrefix.NOTIFICATION);
        List<Notification> fetched = notificationRepository.findPage(
                user, cursorId, PageRequest.of(0, pageRequest.getFetchSize()));

        CursorPageResponse<NotificationItemDto> page = CursorPageResponse
                .of(fetched, pageRequest.getLimit(), notification -> IdPrefix.NOTIFICATION.format(notification.getId()))
                .map(NotificationItemDto::from);

        return GlobalResponse.ok(NotificationListResponseDto.builder()
                .items(page.getItems())
                .unreadCount(notificationRepository.countByUserAndReadAtIsNull(user))
                .nextCursor(page.getNextCursor())
                .build());
    }

    @Transactional
    public GlobalResponse<NotificationReadResponseDto> markRead(String username, String notificationId) {
        User user = userService.getUserEntity(username);
        LocalDateTime now = LocalDateTime.now();

        int readCount;
        if (ALL.equals(notificationId)) {
            readCount = notificationRepository.markAllRead(user, now);
        } else {
            Notification notification = getNotificationEntity(IdPrefix.NOTIFICATION.parse(notificationId), user);
            // 이미 읽은 알림을 다시 읽어도 에러 없이 0건으로 응답한다
            readCount = notification.isRead() ? 0 : 1;
            if (!notification.isRead()) {
                notification.setReadAt(now);
                notificationRepository.saveAndFlush(notification);
            }
        }

        return GlobalResponse.ok(NotificationReadResponseDto.builder()
                .readCount(readCount)
                .unreadCount(notificationRepository.countByUserAndReadAtIsNull(user))
                .build());
    }

    private Notification getNotificationEntity(Long id, User user) {
        return notificationRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> {
                    log.error("[getNotificationEntity] 알림 조회 실패: {} ({})", id, user.getUsername());
                    return new NotificationNotFoundException();
                });
    }
}
