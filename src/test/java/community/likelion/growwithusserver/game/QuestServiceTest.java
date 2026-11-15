package community.likelion.growwithusserver.game;

import community.likelion.growwithusserver.game.data.dto.QuestClaimResponseDto;
import community.likelion.growwithusserver.game.data.dto.QuestResponseDto;
import community.likelion.growwithusserver.game.data.entity.Quest;
import community.likelion.growwithusserver.game.data.entity.UserQuest;
import community.likelion.growwithusserver.game.data.enums.QuestStatus;
import community.likelion.growwithusserver.game.data.enums.QuestType;
import community.likelion.growwithusserver.game.data.exception.QuestAlreadyClaimedException;
import community.likelion.growwithusserver.game.data.exception.QuestNotCompletedException;
import community.likelion.growwithusserver.game.data.exception.QuestNotFoundException;
import community.likelion.growwithusserver.game.data.repository.QuestRepository;
import community.likelion.growwithusserver.game.data.repository.UserQuestRepository;
import community.likelion.growwithusserver.game.service.GameWeek;
import community.likelion.growwithusserver.game.service.QuestService;
import community.likelion.growwithusserver.game.service.StreakService;
import community.likelion.growwithusserver.game.service.WeeklyQuestCreator;
import community.likelion.growwithusserver.common.id.InvalidIdException;
import community.likelion.growwithusserver.garden.GardenFixtures;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.XpHistory;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock
    private QuestRepository questRepository;
    @Mock
    private UserQuestRepository userQuestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;
    @Mock
    private NotificationService notificationService;

    private QuestService questService;
    private Garden garden;
    private LocalDate thisWeek;
    private Quest cheer;
    private Quest heart;
    private Quest event;

    @BeforeEach
    void setUp() {
        questService = new QuestService(questRepository, userQuestRepository, new WeeklyQuestCreator(questRepository),
                userRepository, new GardenService(gardenRepository), new XpService(xpHistoryRepository),
                notificationService);
        garden = GardenFixtures.garden();
        thisWeek = GameWeek.startOf(LocalDate.now());
        cheer = quest(21L, QuestType.CHEER);
        heart = quest(22L, QuestType.HEART);
        event = quest(23L, QuestType.EVENT);

        lenient().when(questRepository.findAllByWeekStartOrderByIdAsc(thisWeek)).thenReturn(List.of(cheer, heart, event));
        lenient().when(questRepository.findById(21L)).thenReturn(Optional.of(cheer));
        lenient().when(userRepository.findByUsername("minji")).thenReturn(Optional.of(garden.getOwner()));
        lenient().when(gardenRepository.findIdByOwnerUsername("minji")).thenReturn(Optional.of(3L));
        lenient().when(gardenRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(garden));
    }

    private Quest quest(Long id, QuestType type) {
        return Quest.builder().id(id).weekStart(thisWeek).type(type).title(type.getTitle())
                .goal(type.getGoal()).rewardXp(type.getRewardXp()).build();
    }

    private UserQuest progress(Quest quest, int progress, boolean claimed) {
        return UserQuest.builder().id(quest.getId() + 100).quest(quest).user(garden.getOwner()).progress(progress).claimed(claimed).build();
    }

    @Test
    void 주간_퀘스트와_진행도를_준다() {
        when(userQuestRepository.findAllByUserIdAndQuestIdIn(1L, List.of(21L, 22L, 23L)))
                .thenReturn(List.of(progress(cheer, 3, false), progress(heart, 3, false), progress(event, 1, true)));

        List<QuestResponseDto> quests = questService.getWeekly("minji").getData();

        assertThat(quests).extracting(QuestResponseDto::getQuestId).containsExactly("qst_21", "qst_22", "qst_23");
        assertThat(quests.get(0).getTitle()).isEqualTo("응원 5번 하기");
        assertThat(quests.get(0).getProgress()).isEqualTo(3);
        assertThat(quests.get(0).getGoal()).isEqualTo(5);
        assertThat(quests.get(0).getRewardXp()).isEqualTo(50);
        assertThat(quests).extracting(QuestResponseDto::getStatus)
                .containsExactly(QuestStatus.IN_PROGRESS, QuestStatus.COMPLETED, QuestStatus.CLAIMED);
    }

    @Test
    void 아직_아무것도_안_했으면_진행도는_0이다() {
        when(userQuestRepository.findAllByUserIdAndQuestIdIn(any(), any())).thenReturn(List.of());

        assertThat(questService.getWeekly("minji").getData())
                .allSatisfy(quest -> {
                    assertThat(quest.getProgress()).isZero();
                    assertThat(quest.getStatus()).isEqualTo(QuestStatus.IN_PROGRESS);
                });
    }

    @Test
    void 이번_주_퀘스트가_없으면_종류마다_하나씩_만든다() {
        when(questRepository.findAllByWeekStartOrderByIdAsc(thisWeek)).thenReturn(List.of(cheer));
        when(questRepository.save(any(Quest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Quest> quests = questService.getWeeklyQuests(thisWeek);

        assertThat(quests).extracting(Quest::getType).containsExactly(QuestType.CHEER, QuestType.HEART, QuestType.EVENT);
        verify(questRepository, times(2)).save(any(Quest.class));
    }

    @Test
    void 동시에_만들다_실패하면_다시_읽는다() {
        when(questRepository.findAllByWeekStartOrderByIdAsc(thisWeek))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(cheer, heart, event));
        when(questRepository.save(any(Quest.class))).thenThrow(new DataIntegrityViolationException("uk_quest_week_type"));

        assertThat(questService.getWeeklyQuests(thisWeek)).containsExactly(cheer, heart, event);
    }

    @Test
    void 응원하면_응원_퀘스트의_진행도가_오른다() {
        when(userQuestRepository.findByQuestIdAndUserId(21L, 1L)).thenReturn(Optional.empty());

        questService.record(garden.getOwner(), QuestType.CHEER);

        ArgumentCaptor<UserQuest> saved = ArgumentCaptor.forClass(UserQuest.class);
        verify(userQuestRepository).save(saved.capture());
        assertThat(saved.getValue().getQuest()).isEqualTo(cheer);
        assertThat(saved.getValue().getProgress()).isEqualTo(1);
        verify(notificationService, never()).send(any());
    }

    @Test
    void 목표를_채우면_주인에게_알리고_그_뒤로는_세지_않는다() {
        UserQuest userQuest = progress(cheer, 4, false);
        when(userQuestRepository.findByQuestIdAndUserId(21L, 1L)).thenReturn(Optional.of(userQuest));

        questService.record(garden.getOwner(), QuestType.CHEER);

        assertThat(userQuest.getProgress()).isEqualTo(5);
        ArgumentCaptor<NotificationSendDto> notification = ArgumentCaptor.forClass(NotificationSendDto.class);
        verify(notificationService).send(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.QUEST_DONE);
        assertThat(notification.getValue().getTargetId()).isEqualTo("qst_21");

        questService.record(garden.getOwner(), QuestType.CHEER);

        assertThat(userQuest.getProgress()).isEqualTo(5);
        verify(notificationService, times(1)).send(any());
    }

    @Test
    void 달성한_퀘스트의_보상을_받는다() {
        UserQuest userQuest = progress(cheer, 5, false);
        when(userQuestRepository.findByQuestIdAndUserId(21L, 1L)).thenReturn(Optional.of(userQuest));

        QuestClaimResponseDto response = questService.claim("minji", "qst_21").getData();

        assertThat(response.getQuestId()).isEqualTo("qst_21");
        assertThat(response.getXpGained()).isEqualTo(50);
        assertThat(response.getTotalXp()).isEqualTo(1290);
        assertThat(response.getStatus()).isEqualTo(QuestStatus.CLAIMED);
        assertThat(userQuest.isClaimed()).isTrue();
        assertThat(userQuest.getClaimedAt()).isNotNull();

        ArgumentCaptor<XpHistory> history = ArgumentCaptor.forClass(XpHistory.class);
        verify(xpHistoryRepository).save(history.capture());
        assertThat(history.getValue().getReason()).isEqualTo(XpReason.QUEST);
    }

    @Test
    void 아직_달성하지_않은_퀘스트는_보상을_받을_수_없다() {
        when(userQuestRepository.findByQuestIdAndUserId(21L, 1L)).thenReturn(Optional.of(progress(cheer, 4, false)));
        assertThatThrownBy(() -> questService.claim("minji", "qst_21")).isInstanceOf(QuestNotCompletedException.class);

        when(userQuestRepository.findByQuestIdAndUserId(21L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> questService.claim("minji", "qst_21")).isInstanceOf(QuestNotCompletedException.class);

        verify(xpHistoryRepository, never()).save(any());
        assertThat(garden.getTotalXp()).isEqualTo(1240);
    }

    @Test
    void 보상은_한_번만_받을_수_있다() {
        when(userQuestRepository.findByQuestIdAndUserId(21L, 1L)).thenReturn(Optional.of(progress(cheer, 5, true)));

        assertThatThrownBy(() -> questService.claim("minji", "qst_21")).isInstanceOf(QuestAlreadyClaimedException.class);
        verify(xpHistoryRepository, never()).save(any());
    }

    @Test
    void 없는_퀘스트와_텃밭이_없는_주인은_보상을_받을_수_없다() {
        when(questRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> questService.claim("minji", "qst_99")).isInstanceOf(QuestNotFoundException.class);
        assertThatThrownBy(() -> questService.claim("minji", "gdn_21")).isInstanceOf(InvalidIdException.class);

        assertThatThrownBy(() -> questService.claim("nobody", "qst_21")).isInstanceOf(GardenNotFoundException.class);
    }

    @Test
    void 스트릭_배치는_전날_응원이_없는_텃밭을_시무룩하게_한다() {
        StreakService streakService = new StreakService(gardenRepository, org.mockito.Mockito.mock(community.likelion.growwithusserver.notification.service.NotificationService.class));
        when(gardenRepository.breakStreaks(LocalDate.now().minusDays(1), CharacterMood.SAD)).thenReturn(2);
        when(gardenRepository.breakStreaks(LocalDate.of(2026, 11, 9), CharacterMood.SAD)).thenReturn(1);

        assertThat(streakService.check(null).getBroken()).isEqualTo(2);
        assertThat(streakService.check(LocalDate.of(2026, 11, 9)).getBroken()).isEqualTo(1);
    }
}
