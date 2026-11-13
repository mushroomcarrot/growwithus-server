package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
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
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.exception.UserNotFoundException;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestService {
    private final QuestRepository questRepository;
    private final UserQuestRepository userQuestRepository;
    private final WeeklyQuestCreator weeklyQuestCreator;
    private final UserRepository userRepository;
    private final GardenService gardenService;
    private final XpService xpService;
    private final NotificationService notificationService;

    // 이번 주 퀘스트와 내 진행도
    @Transactional(readOnly = true)
    public GlobalResponse<List<QuestResponseDto>> getWeekly(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("[getWeekly] 사용자 조회 실패: {}", username);
                    return new UserNotFoundException();
                });
        List<Quest> quests = getWeeklyQuests(GameWeek.startOf(LocalDate.now()));
        Map<Long, UserQuest> progress = userQuestRepository
                .findAllByUserIdAndQuestIdIn(user.getId(), quests.stream().map(Quest::getId).toList()).stream()
                .collect(Collectors.toMap(userQuest -> userQuest.getQuest().getId(), Function.identity()));

        return GlobalResponse.ok(quests.stream()
                .map(quest -> toResponse(quest, progress.get(quest.getId())))
                .toList());
    }

    // 달성한 퀘스트의 보상을 받는다. 텃밭을 잠그므로 동시에 눌러도 보상은 한 번만 나간다
    @Transactional
    public GlobalResponse<QuestClaimResponseDto> claim(String username, String questId) {
        Long id = IdPrefix.QUEST.parse(questId);
        Garden garden = gardenService.findMyGardenForUpdate(username)
                .orElseThrow(() -> {
                    log.error("[claim] 텃밭이 없는 주인의 보상 수령: {}", username);
                    return new GardenNotFoundException();
                });
        Quest quest = questRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("[claim] 퀘스트 조회 실패: {}", questId);
                    return new QuestNotFoundException();
                });

        UserQuest userQuest = userQuestRepository.findByQuestIdAndUserId(quest.getId(), garden.getOwner().getId()).orElse(null);
        if (userQuest != null && userQuest.isClaimed()) {
            log.error("[claim] 이미 보상을 받은 퀘스트: {}", questId);
            throw new QuestAlreadyClaimedException();
        }
        if (userQuest == null || userQuest.getProgress() < quest.getGoal()) {
            log.error("[claim] 아직 달성하지 않은 퀘스트: {}", questId);
            throw new QuestNotCompletedException();
        }

        userQuest.setClaimed(true);
        userQuest.setClaimedAt(LocalDateTime.now());
        int totalXp = xpService.grant(garden, XpReason.QUEST, quest.getRewardXp());

        return GlobalResponse.ok(QuestClaimResponseDto.builder()
                .questId(questId)
                .xpGained(quest.getRewardXp())
                .totalXp(totalXp)
                .status(QuestStatus.CLAIMED)
                .build());
    }

    /**
     * 이번 주 퀘스트의 진행도를 하나 올린다. 응원, 감사 하트, 진화 이벤트 확인이 끝날 때 부른다.
     * 호출한 서비스 메서드의 트랜잭션에 참여하고, 그 주인의 텃밭이 잠긴 상태여야 한다 (같은 주인의 요청이 차례로 처리되도록).
     * 목표를 채우는 순간 주인에게 알린다. 이미 채운 퀘스트는 더 세지 않는다.
     */
    public void record(User user, QuestType type) {
        Quest quest = getWeeklyQuests(GameWeek.startOf(LocalDate.now())).stream()
                .filter(weekly -> weekly.getType() == type)
                .findFirst()
                .orElse(null);
        if (quest == null) {
            log.warn("[record] 이번 주에 없는 퀘스트 종류: {}", type);
            return;
        }

        UserQuest userQuest = userQuestRepository.findByQuestIdAndUserId(quest.getId(), user.getId())
                .orElseGet(() -> UserQuest.builder().quest(quest).user(user).build());
        if (userQuest.getProgress() >= quest.getGoal()) {
            return;
        }

        userQuest.setProgress(userQuest.getProgress() + 1);
        userQuestRepository.save(userQuest);
        if (userQuest.getProgress() >= quest.getGoal()) {
            log.info("[record] 퀘스트 달성: {} ({})", user.getUsername(), type);
            notificationService.send(NotificationSendDto.builder()
                    .receiver(user)
                    .type(NotificationType.QUEST_DONE)
                    .title("주간 퀘스트를 달성했어요")
                    .body(quest.getTitle() + " 보상을 받아 보세요")
                    .targetScreen("DT_LEAGUE")
                    .targetId(IdPrefix.QUEST.format(quest.getId()))
                    .build());
        }
    }

    // 그 주의 퀘스트. 아직 없으면 만든다. 호출한 서비스 메서드의 트랜잭션 안에서 쓴다
    public List<Quest> getWeeklyQuests(LocalDate weekStart) {
        List<Quest> quests = questRepository.findAllByWeekStartOrderByIdAsc(weekStart);
        if (quests.size() >= QuestType.values().length) {
            return quests;
        }

        try {
            return weeklyQuestCreator.findOrCreate(weekStart);
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 방금 만들었다. 새 트랜잭션으로 다시 읽는다
            return weeklyQuestCreator.findOrCreate(weekStart);
        }
    }

    private QuestResponseDto toResponse(Quest quest, UserQuest userQuest) {
        int progress = userQuest == null ? 0 : Math.min(userQuest.getProgress(), quest.getGoal());
        QuestStatus status = userQuest != null && userQuest.isClaimed() ? QuestStatus.CLAIMED
                : progress >= quest.getGoal() ? QuestStatus.COMPLETED
                : QuestStatus.IN_PROGRESS;

        return QuestResponseDto.builder()
                .questId(IdPrefix.QUEST.format(quest.getId()))
                .title(quest.getTitle())
                .progress(progress)
                .goal(quest.getGoal())
                .rewardXp(quest.getRewardXp())
                .status(status)
                .build();
    }
}
