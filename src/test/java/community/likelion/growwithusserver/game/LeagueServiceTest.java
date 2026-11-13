package community.likelion.growwithusserver.game;

import community.likelion.growwithusserver.game.data.dto.LeagueResponseDto;
import community.likelion.growwithusserver.game.data.dto.LeagueSettleResponseDto;
import community.likelion.growwithusserver.game.data.entity.League;
import community.likelion.growwithusserver.game.data.entity.LeagueMember;
import community.likelion.growwithusserver.game.data.entity.Quest;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.data.repository.LeagueMemberRepository;
import community.likelion.growwithusserver.game.data.repository.LeagueRepository;
import community.likelion.growwithusserver.game.data.repository.QuestRepository;
import community.likelion.growwithusserver.game.service.BadgeService;
import community.likelion.growwithusserver.game.service.GameWeek;
import community.likelion.growwithusserver.game.service.LeagueCreator;
import community.likelion.growwithusserver.game.service.LeagueService;
import community.likelion.growwithusserver.game.service.LeagueSettleService;
import community.likelion.growwithusserver.game.service.LeagueSettler;
import community.likelion.growwithusserver.game.service.QuestService;
import community.likelion.growwithusserver.garden.GardenFixtures;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {
    private static final Long FARM_ID = 1L;

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private LeagueCreator leagueCreator;
    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;
    @Mock
    private OwnerProfileRepository ownerProfileRepository;
    @Mock
    private LeagueMemberRepository leagueMemberRepository;
    @Mock
    private QuestRepository questRepository;
    @Mock
    private QuestService questService;
    @Mock
    private BadgeService badgeService;

    private LeagueService leagueService;
    private LeagueSettleService leagueSettleService;
    private Garden mine;
    private Garden neighbor;
    private Garden idle;
    private LocalDate thisWeek;

    @BeforeEach
    void setUp() {
        leagueService = new LeagueService(leagueRepository, leagueCreator, gardenRepository, xpHistoryRepository, ownerProfileRepository);
        leagueSettleService = new LeagueSettleService(leagueService,
                new LeagueSettler(leagueService, badgeService, leagueRepository, leagueMemberRepository),
                questService, questRepository, gardenRepository);
        thisWeek = GameWeek.startOf(LocalDate.now());

        mine = GardenFixtures.garden();
        neighbor = neighborGarden(4L, 11L, "hajun", "김하준");
        idle = neighborGarden(5L, 12L, "seoyeon", "이서연");

        lenient().when(gardenRepository.findByOwnerUsername("minji")).thenReturn(Optional.of(mine));
        lenient().when(gardenRepository.findAllByFarmId(FARM_ID)).thenReturn(List.of(mine, neighbor, idle));
        lenient().when(ownerProfileRepository.findAllByUserIdIn(anyList())).thenReturn(List.of(
                OwnerProfile.builder().user(mine.getOwner()).nickname("민지네").build(),
                OwnerProfile.builder().user(neighbor.getOwner()).nickname("하준이네").build()));
    }

    private Garden neighborGarden(Long gardenId, Long userId, String username, String name) {
        Garden garden = GardenFixtures.garden();
        garden.setId(gardenId);
        garden.setOwner(User.builder().id(userId).username(username).name(name).role(UserRole.OWNER).build());

        return garden;
    }

    private League league(Long id, LocalDate weekStart) {
        return League.builder().id(id).farm(mine.getPlot().getFarm()).weekStart(weekStart).weekEnd(GameWeek.endOf(weekStart)).build();
    }

    // 텃밭 3(나) 210, 텃밭 4(이웃) 320, 텃밭 5는 기록 없음
    private void weeklyXp(LocalDate weekStart) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{3L, 210L});
        rows.add(new Object[]{4L, 320L});
        when(xpHistoryRepository.sumAmountByGarden(anyList(), eq(weekStart.atStartOfDay()), eq(weekStart.plusWeeks(1).atStartOfDay())))
                .thenReturn(rows);
    }

    @Test
    void 한_주는_월요일에_시작해_일요일에_끝난다() {
        LocalDate wednesday = LocalDate.of(2026, 11, 11);

        assertThat(GameWeek.startOf(wednesday)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(GameWeek.startOf(LocalDate.of(2026, 11, 9))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(GameWeek.startOf(LocalDate.of(2026, 11, 15))).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(GameWeek.endOf(GameWeek.startOf(wednesday)).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void 이번_주_리그는_같은_농장_텃밭을_주간_XP순으로_준다() {
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, thisWeek)).thenReturn(Optional.of(league(7L, thisWeek)));
        weeklyXp(thisWeek);

        LeagueResponseDto response = leagueService.getCurrent("minji").getData();

        assertThat(response.getLeagueId()).isEqualTo("lg_7");
        assertThat(response.getWeekStart()).isEqualTo(thisWeek);
        assertThat(response.getWeekEnd()).isEqualTo(thisWeek.plusDays(6));
        assertThat(response.getMyRank()).isEqualTo(2);
        assertThat(response.getMembers()).extracting(LeagueResponseDto.MemberDto::getGardenId).containsExactly("gdn_4", "gdn_3", "gdn_5");
        assertThat(response.getMembers()).extracting(LeagueResponseDto.MemberDto::getRank).containsExactly(1, 2, 3);
        assertThat(response.getMembers()).extracting(LeagueResponseDto.MemberDto::getWeeklyXp).containsExactly(320, 210, 0);
        assertThat(response.getMembers()).extracting(LeagueResponseDto.MemberDto::isMe).containsExactly(false, true, false);
        // 프로필이 없으면 이름으로 대신한다
        assertThat(response.getMembers()).extracting(LeagueResponseDto.MemberDto::getNickname).containsExactly("하준이네", "민지네", "이서연");
    }

    @Test
    void 주간_XP가_같으면_순위도_같다() {
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, thisWeek)).thenReturn(Optional.of(league(7L, thisWeek)));
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{3L, 100L});
        rows.add(new Object[]{4L, 100L});
        when(xpHistoryRepository.sumAmountByGarden(anyList(), any(), any())).thenReturn(rows);

        LeagueResponseDto response = leagueService.getCurrent("minji").getData();

        assertThat(response.getMyRank()).isEqualTo(1);
        assertThat(response.getMembers()).extracting(LeagueResponseDto.MemberDto::getRank).containsExactly(1, 1, 3);
    }

    @Test
    void 이번_주_리그가_없으면_만들고_동시에_만들다_실패하면_다시_읽는다() {
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, thisWeek)).thenReturn(Optional.empty());
        when(leagueCreator.findOrCreate(FARM_ID, thisWeek))
                .thenThrow(new DataIntegrityViolationException("uk_league_farm_week"))
                .thenReturn(league(8L, thisWeek));
        weeklyXp(thisWeek);

        assertThat(leagueService.getCurrent("minji").getData().getLeagueId()).isEqualTo("lg_8");
        verify(leagueCreator, times(2)).findOrCreate(FARM_ID, thisWeek);
    }

    @Test
    void 텃밭이_없으면_리그를_볼_수_없다() {
        when(gardenRepository.findByOwnerUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.getCurrent("nobody")).isInstanceOf(GardenNotFoundException.class);
    }

    @Test
    void 정산하면_지난주_순위를_남기고_1등에게_배지를_주고_새_주를_만든다() {
        LocalDate lastWeek = thisWeek.minusWeeks(1);
        League last = league(6L, lastWeek);
        when(gardenRepository.findFarmIdsWithGarden()).thenReturn(List.of(FARM_ID));
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, lastWeek)).thenReturn(Optional.of(last));
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, thisWeek)).thenReturn(Optional.empty());
        when(leagueRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(last));
        weeklyXp(lastWeek);
        when(questRepository.findAllByWeekStartOrderByIdAsc(thisWeek)).thenReturn(List.of());
        when(questService.getWeeklyQuests(thisWeek)).thenReturn(List.of(new Quest(), new Quest(), new Quest()));

        LeagueSettleResponseDto response = leagueSettleService.settle(null);

        assertThat(response.getSettledLeagues()).isEqualTo(1);
        assertThat(response.getCreatedQuests()).isEqualTo(3);
        assertThat(last.isSettled()).isTrue();
        assertThat(last.getSettledAt()).isNotNull();

        ArgumentCaptor<LeagueMember> members = ArgumentCaptor.forClass(LeagueMember.class);
        verify(leagueMemberRepository, times(3)).save(members.capture());
        assertThat(members.getAllValues()).extracting(member -> member.getGarden().getId()).containsExactly(4L, 3L, 5L);
        assertThat(members.getAllValues()).extracting(LeagueMember::getRank).containsExactly(1, 2, 3);
        assertThat(members.getAllValues()).extracting(LeagueMember::getWeeklyXp).containsExactly(320, 210, 0);

        verify(badgeService).grant(neighbor.getOwner(), BadgeCode.LEAGUE_FIRST);
        verify(badgeService, times(1)).grant(any(), any());
        verify(leagueCreator).findOrCreate(FARM_ID, thisWeek);
    }

    @Test
    void 이미_정산한_리그는_다시_정산하지_않는다() {
        LocalDate lastWeek = thisWeek.minusWeeks(1);
        League last = league(6L, lastWeek);
        last.setSettled(true);
        when(gardenRepository.findFarmIdsWithGarden()).thenReturn(List.of(FARM_ID));
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, lastWeek)).thenReturn(Optional.of(last));
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, thisWeek)).thenReturn(Optional.of(league(7L, thisWeek)));
        when(leagueRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(last));
        when(questRepository.findAllByWeekStartOrderByIdAsc(thisWeek)).thenReturn(List.of(new Quest(), new Quest(), new Quest()));
        when(questService.getWeeklyQuests(thisWeek)).thenReturn(List.of(new Quest(), new Quest(), new Quest()));

        // 수요일을 넘겨도 그 주의 월요일로 맞춘다
        LeagueSettleResponseDto response = leagueSettleService.settle(lastWeek.plusDays(2));

        assertThat(response.getSettledLeagues()).isZero();
        assertThat(response.getCreatedQuests()).isZero();
        verify(leagueMemberRepository, never()).save(any());
        verify(badgeService, never()).grant(any(), any());
    }

    @Test
    void 아무도_XP를_얻지_못한_주에는_1등_배지를_주지_않는다() {
        LocalDate lastWeek = thisWeek.minusWeeks(1);
        League last = league(6L, lastWeek);
        when(gardenRepository.findFarmIdsWithGarden()).thenReturn(List.of(FARM_ID));
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, lastWeek)).thenReturn(Optional.of(last));
        when(leagueRepository.findByFarmIdAndWeekStart(FARM_ID, thisWeek)).thenReturn(Optional.of(league(7L, thisWeek)));
        when(leagueRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(last));
        when(xpHistoryRepository.sumAmountByGarden(anyList(), any(), any())).thenReturn(List.of());
        when(questService.getWeeklyQuests(thisWeek)).thenReturn(List.of());

        assertThat(leagueSettleService.settle(null).getSettledLeagues()).isEqualTo(1);
        verify(leagueMemberRepository, times(3)).save(any());
        verify(badgeService, never()).grant(any(), any());
    }
}
