package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.game.data.entity.League;
import community.likelion.growwithusserver.game.data.entity.LeagueMember;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.data.repository.LeagueMemberRepository;
import community.likelion.growwithusserver.game.data.repository.LeagueRepository;
import community.likelion.growwithusserver.game.service.LeagueRanking.Standing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 리그 하나를 한 트랜잭션으로 정산한다. 농장 하나가 실패해도 다른 농장의 정산은 남는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueSettler {
    private final LeagueService leagueService;
    private final BadgeService badgeService;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository leagueMemberRepository;

    /**
     * 순위를 league_member에 남기고 1등에게 배지를 준다. 리그 행을 먼저 잠그므로 같은 리그를 두 번 정산하지 않는다.
     *
     * @return 이번 호출로 정산했으면 true. 이미 정산한 리그면 false
     */
    @Transactional
    public boolean settle(Long leagueId) {
        League league = leagueRepository.findByIdForUpdate(leagueId).orElseThrow();
        if (league.isSettled()) {
            return false;
        }

        for (Standing standing : leagueService.getStandings(league.getFarm().getId(), league.getWeekStart())) {
            leagueMemberRepository.save(LeagueMember.builder()
                    .league(league)
                    .garden(standing.garden())
                    .weeklyXp(standing.weeklyXp())
                    .rank(standing.rank())
                    .build());
            // 한 주 동안 아무것도 안 한 텃밭은 1등이어도 배지를 주지 않는다
            if (standing.rank() == 1 && standing.weeklyXp() > 0) {
                badgeService.grant(standing.garden().getOwner(), BadgeCode.LEAGUE_FIRST);
            }
        }
        league.setSettled(true);
        league.setSettledAt(LocalDateTime.now());
        log.info("[settle] 리그 정산: {} ({})", leagueId, league.getWeekStart());

        return true;
    }
}
