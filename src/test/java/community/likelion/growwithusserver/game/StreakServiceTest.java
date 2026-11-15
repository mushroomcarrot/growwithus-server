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
}
