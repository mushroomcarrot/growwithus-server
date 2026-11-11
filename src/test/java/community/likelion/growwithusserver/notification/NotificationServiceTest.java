package community.likelion.growwithusserver.notification;

import community.likelion.growwithusserver.common.id.InvalidIdException;
import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.notification.data.dto.NotificationListResponseDto;
import community.likelion.growwithusserver.notification.data.dto.NotificationReadResponseDto;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.entity.Notification;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.data.exception.NotificationNotFoundException;
import community.likelion.growwithusserver.notification.data.repository.NotificationRepository;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserService userService;
    @InjectMocks
    private NotificationService notificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).username("soonja").name("박순자").build();
        lenient().when(userService.getUserEntity("soonja")).thenReturn(user);
    }

    private Notification notification(long id, LocalDateTime readAt) {
        return Notification.builder()
                .id(id)
                .user(user)
                .type(NotificationType.VISIT_RESERVED)
                .title("민지네가 11월 28일에 방문해요")
                .body("오전 10시에 3명이 와요")
                .targetScreen("DT_CARE_TODAY")
                .targetId("vst_3")
                .createdAt(LocalDateTime.of(2026, 11, 10, 10, 21))
                .readAt(readAt)
                .build();
    }

    @Test
    void send는_안_읽은_알림으로_저장한다() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        Notification saved = notificationService.send(NotificationSendDto.builder()
                .receiver(user)
                .type(NotificationType.HEART)
                .title("민지네가 하트를 보냈어요")
                .body("고마워요")
                .targetScreen("DT_CARE_DONE")
                .targetId("cl_5")
                .build());

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getType()).isEqualTo(NotificationType.HEART);
        assertThat(saved.getTargetScreen()).isEqualTo("DT_CARE_DONE");
        assertThat(saved.getTargetId()).isEqualTo("cl_5");
    }

    @Test
    void 목록은_limit만큼_자르고_다음_커서와_안_읽은_수를_준다() {
        CursorPageRequest pageRequest = new CursorPageRequest();
        pageRequest.setLimit(2);
        pageRequest.setCursor("ntf_10");
        when(notificationRepository.findPage(eq(user), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(notification(9, null), notification(8, LocalDateTime.now()), notification(7, null)));
        when(notificationRepository.countByUserAndReadAtIsNull(user)).thenReturn(3L);

        NotificationListResponseDto response = notificationService.getNotifications("soonja", pageRequest).getData();

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getNotificationId()).isEqualTo("ntf_9");
        assertThat(response.getItems().get(0).isRead()).isFalse();
        assertThat(response.getItems().get(0).getTarget().getScreen()).isEqualTo("DT_CARE_TODAY");
        assertThat(response.getItems().get(0).getTarget().getId()).isEqualTo("vst_3");
        assertThat(response.getItems().get(1).isRead()).isTrue();
        assertThat(response.getNextCursor()).isEqualTo("ntf_8");
        assertThat(response.getUnreadCount()).isEqualTo(3L);
    }

    @Test
    void all이면_모두_읽음_처리한다() {
        when(notificationRepository.markAllRead(eq(user), any(LocalDateTime.class))).thenReturn(3);
        when(notificationRepository.countByUserAndReadAtIsNull(user)).thenReturn(0L);

        NotificationReadResponseDto response = notificationService.markRead("soonja", "all").getData();

        assertThat(response.getReadCount()).isEqualTo(3);
        assertThat(response.getUnreadCount()).isZero();
    }

    @Test
    void 한_건을_읽음_처리한다() {
        Notification unread = notification(9, null);
        when(notificationRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(unread));
        when(notificationRepository.countByUserAndReadAtIsNull(user)).thenReturn(2L);

        NotificationReadResponseDto response = notificationService.markRead("soonja", "ntf_9").getData();

        assertThat(unread.isRead()).isTrue();
        assertThat(response.getReadCount()).isEqualTo(1);
        assertThat(response.getUnreadCount()).isEqualTo(2L);
        verify(notificationRepository).saveAndFlush(unread);
    }

    @Test
    void 이미_읽은_알림은_0건으로_응답한다() {
        LocalDateTime readAt = LocalDateTime.of(2026, 11, 10, 11, 0);
        Notification read = notification(9, readAt);
        when(notificationRepository.findByIdAndUser(9L, user)).thenReturn(Optional.of(read));

        NotificationReadResponseDto response = notificationService.markRead("soonja", "ntf_9").getData();

        assertThat(response.getReadCount()).isZero();
        assertThat(read.getReadAt()).isEqualTo(readAt);
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 남의_알림이나_없는_알림은_C404다() {
        when(notificationRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead("soonja", "ntf_99"))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void 알림_ID_형식이_틀리면_C002다() {
        assertThatThrownBy(() -> notificationService.markRead("soonja", "ALL"))
                .isInstanceOf(InvalidIdException.class);
    }
}
