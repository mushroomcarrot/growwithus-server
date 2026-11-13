package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.game.data.dto.LeagueResponseDto;
import community.likelion.growwithusserver.game.data.entity.League;
import community.likelion.growwithusserver.game.data.repository.LeagueRepository;
import community.likelion.growwithusserver.game.service.LeagueRanking.Standing;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueService {
    private final LeagueRepository leagueRepository;
    private final LeagueCreator leagueCreator;
    private final GardenRepository gardenRepository;
    private final XpHistoryRepository xpHistoryRepository;
    private final OwnerProfileRepository ownerProfileRepository;

    // 이번 주 리그와 내 순위. 순위는 이번 주에 얻은 XP로 그때그때 계산한다
    @Transactional(readOnly = true)
    public GlobalResponse<LeagueResponseDto> getCurrent(String username) {
        Garden myGarden = gardenRepository.findByOwnerUsername(username)
                .orElseThrow(() -> {
                    log.error("[getCurrent] 텃밭이 없는 주인의 리그 조회: {}", username);
                    return new GardenNotFoundException();
                });
        Long farmId = myGarden.getPlot().getFarm().getId();
        LocalDate weekStart = GameWeek.startOf(LocalDate.now());

        League league = getLeague(farmId, weekStart);
        List<Standing> standings = getStandings(farmId, weekStart);
        Map<Long, String> nicknames = ownerProfileRepository
                .findAllByUserIdIn(standings.stream().map(standing -> standing.garden().getOwner().getId()).toList()).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), OwnerProfile::getNickname));

        return GlobalResponse.ok(LeagueResponseDto.builder()
                .leagueId(IdPrefix.LEAGUE.format(league.getId()))
                .weekStart(league.getWeekStart())
                .weekEnd(league.getWeekEnd())
                .myRank(standings.stream()
                        .filter(standing -> standing.garden().getId().equals(myGarden.getId()))
                        .findFirst()
                        .map(Standing::rank)
                        .orElse(0))
                .members(standings.stream()
                        .map(standing -> LeagueResponseDto.MemberDto.builder()
                                .rank(standing.rank())
                                .gardenId(IdPrefix.GARDEN.format(standing.garden().getId()))
                                .nickname(nicknames.getOrDefault(standing.garden().getOwner().getId(),
                                        standing.garden().getOwner().getName()))
                                .weeklyXp(standing.weeklyXp())
                                .me(standing.garden().getId().equals(myGarden.getId()))
                                .build())
                        .toList())
                .build());
    }

    // 그 농장의 그 주 리그. 없으면 만든다. 호출한 서비스 메서드의 트랜잭션 안에서 쓴다
    public League getLeague(Long farmId, LocalDate weekStart) {
        return leagueRepository.findByFarmIdAndWeekStart(farmId, weekStart).orElseGet(() -> {
            try {
                return leagueCreator.findOrCreate(farmId, weekStart);
            } catch (DataIntegrityViolationException e) {
                // 다른 요청이 방금 만들었다. 새 트랜잭션으로 다시 읽는다
                return leagueCreator.findOrCreate(farmId, weekStart);
            }
        });
    }

    // 그 농장 텃밭들의 그 주 순위
    public List<Standing> getStandings(Long farmId, LocalDate weekStart) {
        // 그 주가 끝나기 전에 시작한 텃밭만 리그에 든다
        List<Garden> gardens = gardenRepository.findAllByFarmId(farmId).stream()
                .filter(garden -> garden.getCreatedAt() == null
                        || garden.getCreatedAt().isBefore(weekStart.plusWeeks(1).atStartOfDay()))
                .toList();
        if (gardens.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> weeklyXp = xpHistoryRepository
                .sumAmountByGarden(gardens.stream().map(Garden::getId).toList(), weekStart.atStartOfDay(),
                        weekStart.plusWeeks(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));

        return LeagueRanking.rank(gardens, weeklyXp);
    }
}
