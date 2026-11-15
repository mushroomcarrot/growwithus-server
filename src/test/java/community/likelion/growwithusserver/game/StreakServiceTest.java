package community.likelion.growwithusserver.game;

import community.likelion.growwithusserver.game.service.StreakService;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreakServiceTest {
    private final GardenRepository gardenRepository = mock(GardenRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final StreakService streakService = new StreakService(gardenRepository, notificationService);

    @Test
    void 전날_응원이_없는_텃밭의_스트릭을_끊는다() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(gardenRepository.breakStreaks(yesterday, CharacterMood.SAD)).thenReturn(2);

        assertThat(streakService.check(null).getBroken()).isEqualTo(2);
    }

    @Test
    void 오늘_응원하지_않은_주인에게_하루_한_번만_스트릭_위험을_알린다() {
        LocalDate today = LocalDate.now();
        User minji = User.builder().id(1L).username("minji").build();
        User other = User.builder().id(2L).username("other").build();
        Garden first = Garden.builder().id(3L).owner(minji).characterName("토순이").streakDays(12).build();
        Garden second = Garden.builder().id(4L).owner(other).characterName("토순이").streakDays(2).build();
        when(gardenRepository.findStreaksAtRisk(today)).thenReturn(List.of(first, second));
        // other는 오늘 이미 알림을 받았다
        when(notificationService.hasSentSince(other, NotificationType.STREAK_RISK, today.atStartOfDay())).thenReturn(true);

        assertThat(streakService.remind().getReminded()).isEqualTo(1);

        ArgumentCaptor<NotificationSendDto> sent = ArgumentCaptor.forClass(NotificationSendDto.class);
        verify(notificationService, times(1)).send(sent.capture());
        assertThat(sent.getValue().getReceiver()).isSameAs(minji);
        assertThat(sent.getValue().getType()).isEqualTo(NotificationType.STREAK_RISK);
        assertThat(sent.getValue().getBody()).contains("12일째");
        assertThat(sent.getValue().getTargetScreen()).isEqualTo("DT_HOME");
        assertThat(sent.getValue().getTargetId()).isEqualTo("gdn_3");
    }

    @Test
    void 위험한_텃밭이_없으면_알림을_보내지_않는다() {
        when(gardenRepository.findStreaksAtRisk(any())).thenReturn(List.of());

        assertThat(streakService.remind().getReminded()).isZero();
        verify(notificationService, times(0)).send(any());
    }
}
