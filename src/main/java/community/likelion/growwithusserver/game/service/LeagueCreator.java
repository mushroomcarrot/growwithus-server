package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.game.data.entity.League;
import community.likelion.growwithusserver.game.data.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 리그를 따로 떼어 낸 트랜잭션에서 만든다. 같은 농장의 여러 주인이 동시에 그 주의 리그를 처음 찾으면
 * 한쪽은 유니크 제약에 걸리는데, 그 실패가 부른 쪽의 트랜잭션까지 되돌리지 않게 하려는 것이다.
 * 부르는 쪽(LeagueService.getLeague)이 실패하면 한 번 더 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueCreator {
    private final LeagueRepository leagueRepository;
    private final FarmRepository farmRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public League findOrCreate(Long farmId, LocalDate weekStart) {
        return leagueRepository.findByFarmIdAndWeekStart(farmId, weekStart).orElseGet(() -> {
            log.info("[findOrCreate] 리그 생성: 농장 {} ({})", farmId, weekStart);
            return leagueRepository.save(League.builder()
                    .farm(farmRepository.getReferenceById(farmId))
                    .weekStart(weekStart)
                    .weekEnd(GameWeek.endOf(weekStart))
                    .build());
        });
    }
}
