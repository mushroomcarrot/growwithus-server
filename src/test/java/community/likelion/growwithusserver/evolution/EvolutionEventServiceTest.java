package community.likelion.growwithusserver.evolution;

import community.likelion.growwithusserver.evolution.data.dto.EventAckResponseDto;
import community.likelion.growwithusserver.evolution.data.dto.EvolutionEventResponseDto;
import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.evolution.data.exception.EvolutionEventAlreadyAckedException;
import community.likelion.growwithusserver.evolution.data.exception.EvolutionEventNotFoundException;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.game.data.entity.Badge;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.service.BadgeService;
import community.likelion.growwithusserver.game.service.QuestService;
import community.likelion.growwithusserver.garden.GardenFixtures;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
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
class EvolutionEventServiceTest {

    @Mock
    private EvolutionEventRepository evolutionEventRepository;
    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;
    @Mock
    private BadgeService badgeService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private QuestService questService;

    private EvolutionEventService evolutionEventService;
    private Garden garden;

    @BeforeEach
    void setUp() {
        evolutionEventService = new EvolutionEventService(evolutionEventRepository, new GardenService(gardenRepository),
                new XpService(xpHistoryRepository), badgeService, notificationService, questService);
        garden = GardenFixtures.garden();
        lenient().when(evolutionEventRepository.findGardenIdById(7L)).thenReturn(Optional.of(3L));

        lenient().when(gardenRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(garden));
        lenient().when(gardenRepository.findDetail(3L)).thenReturn(Optional.of(garden));
        lenient().when(evolutionEventRepository.save(any(EvolutionEvent.class))).thenAnswer(invocation -> {
            EvolutionEvent event = invocation.getArgument(0);
            event.setId(7L);
            return event;
        });
    }

    private EvolutionEvent fruitEvent() {
        return EvolutionEvent.builder().id(7L).garden(garden).fromStage(GrowthStage.FLOWER).toStage(GrowthStage.FRUIT)
                .occurredAt(LocalDateTime.now()).rewardXp(100).rewardBadgeCode("FIRST_FRUIT").build();
    }

    @Test
    void 단계가_오르면_이벤트가_생기고_주인에게_알린다() {
        EvolutionEvent event = evolutionEventService.advanceStage(3L, GrowthStage.FRUIT).orElseThrow();

        assertThat(garden.getStage()).isEqualTo(GrowthStage.FRUIT);
        assertThat(event.getFromStage()).isEqualTo(GrowthStage.FLOWER);
        assertThat(event.getToStage()).isEqualTo(GrowthStage.FRUIT);
        assertThat(event.getRewardXp()).isEqualTo(100);
        assertThat(event.getRewardBadgeCode()).isEqualTo("FIRST_FRUIT");
        assertThat(event.isAcked()).isFalse();

        ArgumentCaptor<NotificationSendDto> notification = ArgumentCaptor.forClass(NotificationSendDto.class);
        verify(notificationService).send(notification.capture());
        assertThat(notification.getValue().getReceiver().getUsername()).isEqualTo("minji");
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.STAGE_UP);
        assertThat(notification.getValue().getTargetId()).isEqualTo("evt_7");
    }

    @Test
    void 단계가_오르지_않으면_아무것도_하지_않는다() {
        assertThat(evolutionEventService.advanceStage(3L, GrowthStage.FLOWER)).isEmpty();
        assertThat(evolutionEventService.advanceStage(3L, GrowthStage.SPROUT)).isEmpty();

        assertThat(garden.getStage()).isEqualTo(GrowthStage.FLOWER);
        verify(evolutionEventRepository, never()).save(any());
        verify(notificationService, never()).send(any());
    }

    @Test
    void 미확인_이벤트_목록을_준다() {
        when(evolutionEventRepository.findAllByGardenIdAndAckedFalseOrderByIdAsc(3L)).thenReturn(List.of(fruitEvent()));

        List<EvolutionEventResponseDto> events = evolutionEventService.getUnackedEvents("minji", "gdn_3").getData();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventId()).isEqualTo("evt_7");
        assertThat(events.get(0).getReward().getXp()).isEqualTo(100);
        assertThat(events.get(0).getReward().getBadgeCode()).isEqualTo("FIRST_FRUIT");
    }

    @Test
    void 이벤트를_확인하면_XP와_배지를_받는다() {
        EvolutionEvent event = fruitEvent();
        when(evolutionEventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(badgeService.grant(garden.getOwner(), BadgeCode.FIRST_FRUIT))
                .thenReturn(Optional.of(Badge.builder().code("FIRST_FRUIT").name("첫 열매").build()));

        EventAckResponseDto response = evolutionEventService.ack("minji", "evt_7").getData();

        assertThat(response.getXpGained()).isEqualTo(100);
        assertThat(response.getTotalXp()).isEqualTo(1340);
        assertThat(response.getBadge().getCode()).isEqualTo("FIRST_FRUIT");
        assertThat(response.getBadge().getName()).isEqualTo("첫 열매");
        assertThat(event.isAcked()).isTrue();
        assertThat(event.getAckedAt()).isNotNull();
        var order = org.mockito.Mockito.inOrder(gardenRepository, evolutionEventRepository);
        order.verify(evolutionEventRepository).findGardenIdById(7L);
        order.verify(gardenRepository).findByIdForUpdate(3L);
        order.verify(evolutionEventRepository).findByIdForUpdate(7L);
    }

    @Test
    void 이미_확인한_이벤트는_보상을_다시_주지_않는다() {
        EvolutionEvent event = fruitEvent();
        event.setAcked(true);
        when(evolutionEventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> evolutionEventService.ack("minji", "evt_7"))
                .isInstanceOf(EvolutionEventAlreadyAckedException.class);
        verify(xpHistoryRepository, never()).save(any());
        verify(badgeService, never()).grant(any(), any());
        assertThat(garden.getTotalXp()).isEqualTo(1240);
    }

    @Test
    void 남의_이벤트와_없는_이벤트는_확인할_수_없다() {
        lenient().when(evolutionEventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(fruitEvent()));
        assertThatThrownBy(() -> evolutionEventService.ack("other", "evt_7")).isInstanceOf(GardenAccessDeniedException.class);

        when(evolutionEventRepository.findGardenIdById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> evolutionEventService.ack("minji", "evt_9")).isInstanceOf(EvolutionEventNotFoundException.class);
    }
}
