package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.game.data.entity.Quest;
import community.likelion.growwithusserver.game.data.enums.QuestType;
import community.likelion.growwithusserver.game.data.repository.QuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 그 주의 퀘스트를 따로 떼어 낸 트랜잭션에서 만든다. 이유는 LeagueCreator와 같다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyQuestCreator {
    private final QuestRepository questRepository;

    // 그 주의 퀘스트를 돌려준다. QuestType 중 아직 없는 종류는 만들어서 채운다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Quest> findOrCreate(LocalDate weekStart) {
        List<Quest> quests = new ArrayList<>(questRepository.findAllByWeekStartOrderByIdAsc(weekStart));
        for (QuestType type : QuestType.values()) {
            if (quests.stream().noneMatch(quest -> quest.getType() == type)) {
                quests.add(questRepository.save(Quest.builder()
                        .weekStart(weekStart)
                        .type(type)
                        .title(type.getTitle())
                        .goal(type.getGoal())
                        .rewardXp(type.getRewardXp())
                        .build()));
                log.info("[findOrCreate] 주간 퀘스트 생성: {} ({})", type, weekStart);
            }
        }

        return quests;
    }
}
