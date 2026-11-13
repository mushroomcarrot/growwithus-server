package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.game.data.dto.LeagueSettleResponseDto;
import community.likelion.growwithusserver.game.data.repository.QuestRepository;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 주간 리그 정산 배치 (매주 월요일 00:10). 지난주 순위를 확정하고 새 주의 리그와 퀘스트를 만든다.
 *
 * 전체를 한 트랜잭션으로 묶지 않는다. 리그를 만드는 것과 정산하는 것이 각각의 트랜잭션이어야
 * 방금 만든 리그를 정산할 때 잠글 수 있다 (GardenService의 설명 참고).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueSettleService {
    private final LeagueService leagueService;
    private final LeagueSettler leagueSettler;
    private final QuestService questService;
    private final QuestRepository questRepository;
    private final GardenRepository gardenRepository;

    /**
     * @param date 정산할 주에 속한 날. null이면 지난주. 이미 정산한 리그는 건너뛰므로 다시 실행해도 된다
     */
    public LeagueSettleResponseDto settle(LocalDate date) {
        LocalDate weekStart = date == null ? GameWeek.startOf(LocalDate.now()).minusWeeks(1) : GameWeek.startOf(date);
        LocalDate nextWeekStart = weekStart.plusWeeks(1);

        int settledLeagues = 0;
        for (Long farmId : gardenRepository.findFarmIdsWithGarden()) {
            if (leagueSettler.settle(leagueService.getLeague(farmId, weekStart).getId())) {
                settledLeagues++;
            }
            leagueService.getLeague(farmId, nextWeekStart);
        }

        int questsBefore = questRepository.findAllByWeekStartOrderByIdAsc(nextWeekStart).size();
        int createdQuests = questService.getWeeklyQuests(nextWeekStart).size() - questsBefore;
        log.info("[settle] 리그 정산 완료: {} (리그 {}개, 새 퀘스트 {}개)", weekStart, settledLeagues, createdQuests);

        return LeagueSettleResponseDto.builder()
                .settledLeagues(settledLeagues)
                .createdQuests(createdQuests)
                .build();
    }
}
