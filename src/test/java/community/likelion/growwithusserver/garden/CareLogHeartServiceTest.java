package community.likelion.growwithusserver.garden;

import community.likelion.growwithusserver.garden.care.GardenCareLog;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.dto.HeartResponseDto;
import community.likelion.growwithusserver.garden.data.entity.CareLogHeart;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.exception.CareLogNotFoundException;
import community.likelion.growwithusserver.garden.data.exception.HeartAlreadySentException;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.garden.service.CareLogHeartService;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareLogHeartServiceTest {

    @Mock
    private GardenCareLogReader gardenCareLogReader;
    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;
    @Mock
    private CareLogHeartRepository careLogHeartRepository;
    @Mock
    private OwnerProfileRepository ownerProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private CareLogHeartService careLogHeartService;
    private Garden garden;

    @BeforeEach
    void setUp() {
        careLogHeartService = new CareLogHeartService(gardenCareLogReader, new GardenService(gardenRepository),
                new XpService(xpHistoryRepository), careLogHeartRepository, ownerProfileRepository, userRepository,
                notificationService);
        garden = GardenFixtures.garden();

        lenient().when(gardenCareLogReader.findCareLog(11L)).thenReturn(Optional.of(new GardenCareLog(11L, 3L, "soonja",
                List.of("SUPPORT"), "지지대를 세웠어요", null, LocalDateTime.now())));
        lenient().when(gardenRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(garden));
        lenient().when(userRepository.findByUsername("soonja")).thenReturn(Optional.of(garden.getCaretaker()));
        lenient().when(ownerProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(OwnerProfile.builder().nickname("민지네").build()));
    }

    @Test
    void 하트를_보내면_XP를_받고_관리사에게_알림이_간다() {
        HeartResponseDto response = careLogHeartService.sendHeart("minji", "cl_11").getData();

        assertThat(response.getCareLogId()).isEqualTo("cl_11");
        assertThat(response.isHearted()).isTrue();
        assertThat(response.getXpGained()).isEqualTo(5);
        assertThat(garden.getTotalXp()).isEqualTo(1245);

        ArgumentCaptor<CareLogHeart> heart = ArgumentCaptor.forClass(CareLogHeart.class);
        verify(careLogHeartRepository).save(heart.capture());
        assertThat(heart.getValue().getCareLogId()).isEqualTo(11L);
        assertThat(heart.getValue().getCaretakerUsername()).isEqualTo("soonja");

        ArgumentCaptor<NotificationSendDto> notification = ArgumentCaptor.forClass(NotificationSendDto.class);
        verify(notificationService).send(notification.capture());
        assertThat(notification.getValue().getReceiver().getUsername()).isEqualTo("soonja");
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.HEART);
        assertThat(notification.getValue().getTitle()).contains("민지네");
        assertThat(notification.getValue().getTargetId()).isEqualTo("cl_11");
    }

    @Test
    void 돌봄_기록_하나에_하트는_한_번만_보낼_수_있다() {
        when(careLogHeartRepository.existsByCareLogId(11L)).thenReturn(true);

        assertThatThrownBy(() -> careLogHeartService.sendHeart("minji", "cl_11")).isInstanceOf(HeartAlreadySentException.class);
        verify(careLogHeartRepository, never()).save(any());
        verify(notificationService, never()).send(any());
        assertThat(garden.getTotalXp()).isEqualTo(1240);
    }

    @Test
    void 남의_텃밭의_돌봄_기록에는_하트를_보낼_수_없다() {
        assertThatThrownBy(() -> careLogHeartService.sendHeart("other", "cl_11")).isInstanceOf(GardenAccessDeniedException.class);
    }

    @Test
    void 없는_돌봄_기록에는_하트를_보낼_수_없다() {
        when(gardenCareLogReader.findCareLog(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> careLogHeartService.sendHeart("minji", "cl_99")).isInstanceOf(CareLogNotFoundException.class);
    }
}
