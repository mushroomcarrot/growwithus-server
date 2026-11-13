package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.time.DateTimes;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.service.FarmService;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.data.enums.QuestType;
import community.likelion.growwithusserver.game.service.BadgeService;
import community.likelion.growwithusserver.game.service.QuestService;
import community.likelion.growwithusserver.garden.care.GardenCareLog;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.dto.CheerResponseDto;
import community.likelion.growwithusserver.garden.data.dto.GardenHomeResponseDto;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.data.exception.CheerAlreadyDoneException;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import community.likelion.growwithusserver.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GardenHomeService {
    public static final int CHEER_XP = 10;
    // 이 일수만큼 이어서 응원하면 배지를 준다
    public static final int STREAK_BADGE_DAYS = 7;

    private final GardenService gardenService;
    private final XpService xpService;
    private final WeatherService weatherService;
    private final FarmService farmService;
    private final GardenCareLogReader gardenCareLogReader;
    private final CareLogHeartRepository careLogHeartRepository;
    private final EvolutionEventRepository evolutionEventRepository;
    private final QuestService questService;
    private final BadgeService badgeService;

    // 홈 화면을 한 번에 조회한다. 텃밭과 그 연관은 조회 한 번(fetch join)으로 가져온다
    @Transactional(readOnly = true)
    public GlobalResponse<GardenHomeResponseDto> getHome(String username, String gardenId) {
        Garden garden = gardenService.getOwnedGarden(username, gardenId);
        Farm farm = garden.getPlot().getFarm();
        LocalDate today = LocalDate.now();

        CurrentWeatherDto weather = weatherService.getCurrentWeather(farm.getNx(), farm.getNy()).orElse(null);
        WeatherCondition condition = weather == null ? null : weather.getCondition();

        return GlobalResponse.ok(GardenHomeResponseDto.builder()
                .gardenId(IdPrefix.GARDEN.format(garden.getId()))
                .character(GardenHomeResponseDto.CharacterDto.builder()
                        .name(garden.getCharacterName())
                        .stage(garden.getStage())
                        .level(garden.getStage().getLevel())
                        .mood(garden.getMood())
                        .animation(CharacterAnimation.resolve(garden.getMood(), condition))
                        .message(CharacterAnimation.message(garden.getMood(), condition))
                        .build())
                .weather(weather)
                .streak(new GardenHomeResponseDto.StreakDto(garden.getStreakDays(), today.equals(garden.getLastCheeredOn())))
                .xp(new GardenHomeResponseDto.XpDto(garden.getTotalXp(), xpService.getTodayXp(garden.getId())))
                .harvest(toHarvest(garden.getExpectedHarvestDate(), today))
                .caretaker(farmService.toCaretakerSummary(garden.getCaretaker()))
                .recentCareLog(gardenCareLogReader.findLatest(garden.getId()).map(this::toRecentCareLog).orElse(null))
                .hasUnreadEvent(evolutionEventRepository.existsByGardenIdAndAckedFalse(garden.getId()))
                .build());
    }

    // 오늘의 응원. 하루 한 번이고, 어제도 응원했으면 스트릭이 이어지고 아니면 1일부터 다시 센다
    @Transactional
    public GlobalResponse<CheerResponseDto> cheer(String username, String gardenId) {
        Garden garden = gardenService.getOwnedGardenForUpdate(username, gardenId);
        LocalDate today = LocalDate.now();

        if (today.equals(garden.getLastCheeredOn())) {
            log.error("[cheer] 오늘 이미 응원한 텃밭: {}", gardenId);
            throw new CheerAlreadyDoneException();
        }

        boolean cheeredYesterday = today.minusDays(1).equals(garden.getLastCheeredOn());
        garden.setStreakDays(cheeredYesterday ? garden.getStreakDays() + 1 : 1);
        garden.setLastCheeredOn(today);
        garden.setMood(CharacterMood.HAPPY);
        int totalXp = xpService.grant(garden, XpReason.CHEER, CHEER_XP);
        questService.record(garden.getOwner(), QuestType.CHEER);
        if (garden.getStreakDays() >= STREAK_BADGE_DAYS) {
            badgeService.grant(garden.getOwner(), BadgeCode.STREAK_7);
        }

        return GlobalResponse.ok(CheerResponseDto.builder()
                .streakDays(garden.getStreakDays())
                .xpGained(CHEER_XP)
                .totalXp(totalXp)
                .mood(garden.getMood())
                .build());
    }

    private GardenHomeResponseDto.HarvestDto toHarvest(LocalDate expectedDate, LocalDate today) {
        Integer dDay = expectedDate == null ? null : (int) Math.max(0, ChronoUnit.DAYS.between(today, expectedDate));

        return new GardenHomeResponseDto.HarvestDto(expectedDate, dDay);
    }

    private GardenHomeResponseDto.RecentCareLogDto toRecentCareLog(GardenCareLog careLog) {
        return GardenHomeResponseDto.RecentCareLogDto.builder()
                .careLogId(IdPrefix.CARE_LOG.format(careLog.careLogId()))
                .summary(careLog.summary())
                .photoUrl(careLog.photoUrl())
                .createdAt(DateTimes.toOffset(careLog.createdAt()))
                .hearted(careLogHeartRepository.existsByCareLogId(careLog.careLogId()))
                .build();
    }
}
