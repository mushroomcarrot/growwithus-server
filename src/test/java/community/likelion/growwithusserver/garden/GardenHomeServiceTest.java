package community.likelion.growwithusserver.garden;

import community.likelion.growwithusserver.farm.caretaker.FarmCaretakerReader;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.farm.service.FarmService;
import community.likelion.growwithusserver.garden.care.GardenCareLog;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.dto.CheerResponseDto;
import community.likelion.growwithusserver.garden.data.dto.GardenHomeResponseDto;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.XpHistory;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.data.exception.CheerAlreadyDoneException;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.garden.service.GardenHomeService;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import community.likelion.growwithusserver.weather.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GardenHomeServiceTest {

    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;
    @Mock
    private WeatherService weatherService;
    @Mock
    private FarmCaretakerReader farmCaretakerReader;
    @Mock
    private GardenCareLogReader gardenCareLogReader;
    @Mock
    private CareLogHeartRepository careLogHeartRepository;
    @Mock
    private FarmRepository farmRepository;
    @Mock
    private PlotRepository plotRepository;
    @Mock
    private CropRepository cropRepository;

    private GardenHomeService gardenHomeService;
    private Garden garden;

    @BeforeEach
    void setUp() {
        gardenHomeService = new GardenHomeService(new GardenService(gardenRepository), new XpService(xpHistoryRepository),
                weatherService, new FarmService(farmRepository, plotRepository, cropRepository, farmCaretakerReader),
                gardenCareLogReader, careLogHeartRepository);
        garden = GardenFixtures.garden();

        lenient().when(gardenRepository.findDetail(3L)).thenReturn(Optional.of(garden));
        lenient().when(gardenRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(garden));
        lenient().when(farmCaretakerReader.findByUsername("soonja")).thenReturn(Optional.empty());
        lenient().when(gardenCareLogReader.findLatest(3L)).thenReturn(Optional.empty());
        lenient().when(weatherService.getCurrentWeather(64, 119)).thenReturn(Optional.empty());
    }

    private void weather(WeatherCondition condition) {
        when(weatherService.getCurrentWeather(64, 119))
                .thenReturn(Optional.of(CurrentWeatherDto.builder().condition(condition).temperature(14.2).build()));
    }

    @Test
    void 홈은_캐릭터와_스트릭과_수확_디데이를_준다() {
        weather(WeatherCondition.SUNNY);
        garden.setStreakDays(12);
        garden.setLastCheeredOn(LocalDate.now().minusDays(1));
        garden.setExpectedHarvestDate(LocalDate.now().plusDays(18));
        when(xpHistoryRepository.sumAmount(eq(3L), any(), any())).thenReturn(15L);

        GardenHomeResponseDto home = gardenHomeService.getHome("minji", "gdn_3").getData();

        assertThat(home.getGardenId()).isEqualTo("gdn_3");
        assertThat(home.getCharacter().getName()).isEqualTo("토순이");
        assertThat(home.getCharacter().getLevel()).isEqualTo(3);
        assertThat(home.getCharacter().getAnimation()).isEqualTo("sunny_happy");
        assertThat(home.getWeather().getCondition()).isEqualTo(WeatherCondition.SUNNY);
        assertThat(home.getStreak().getDays()).isEqualTo(12);
        assertThat(home.getStreak().isCheeredToday()).isFalse();
        assertThat(home.getXp().getTotal()).isEqualTo(1240);
        assertThat(home.getXp().getToday()).isEqualTo(15);
        assertThat(home.getHarvest().getDDay()).isEqualTo(18);
        assertThat(home.getCaretaker().getName()).isEqualTo("박순자");
        assertThat(home.getRecentCareLog()).isNull();
    }

    @Test
    void 비가_오면_우산을_쓰고_더우면_땀을_흘린다() {
        weather(WeatherCondition.RAIN);
        assertThat(gardenHomeService.getHome("minji", "gdn_3").getData().getCharacter().getAnimation()).isEqualTo("rain_umbrella");

        weather(WeatherCondition.HOT);
        assertThat(gardenHomeService.getHome("minji", "gdn_3").getData().getCharacter().getAnimation()).isEqualTo("hot_sweat");
    }

    @Test
    void 시무룩하면_날씨와_상관없이_축_처진다() {
        weather(WeatherCondition.RAIN);
        garden.setMood(CharacterMood.SAD);

        assertThat(gardenHomeService.getHome("minji", "gdn_3").getData().getCharacter().getAnimation()).isEqualTo("sad_droop");
    }

    @Test
    void 수집된_날씨와_수확일이_없어도_홈을_준다() {
        GardenHomeResponseDto home = gardenHomeService.getHome("minji", "gdn_3").getData();

        assertThat(home.getWeather()).isNull();
        assertThat(home.getCharacter().getAnimation()).isEqualTo("sunny_happy");
        assertThat(home.getHarvest().getExpectedDate()).isNull();
        assertThat(home.getHarvest().getDDay()).isNull();
    }

    @Test
    void 최근_돌봄_기록과_하트_여부를_준다() {
        when(gardenCareLogReader.findLatest(3L)).thenReturn(Optional.of(new GardenCareLog(11L, 3L, "soonja",
                List.of("SUPPORT"), "지지대를 세웠어요", "http://photo", LocalDateTime.now())));
        when(careLogHeartRepository.existsByCareLogId(11L)).thenReturn(true);

        GardenHomeResponseDto.RecentCareLogDto recent = gardenHomeService.getHome("minji", "gdn_3").getData().getRecentCareLog();

        assertThat(recent.getCareLogId()).isEqualTo("cl_11");
        assertThat(recent.getSummary()).isEqualTo("지지대를 세웠어요");
        assertThat(recent.isHearted()).isTrue();
    }

    @Test
    void 남의_텃밭과_없는_텃밭은_볼_수_없다() {
        assertThatThrownBy(() -> gardenHomeService.getHome("other", "gdn_3")).isInstanceOf(GardenAccessDeniedException.class);

        when(gardenRepository.findDetail(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gardenHomeService.getHome("minji", "gdn_9")).isInstanceOf(GardenNotFoundException.class);
    }

    @Test
    void 어제도_응원했으면_스트릭이_이어진다() {
        garden.setStreakDays(12);
        garden.setLastCheeredOn(LocalDate.now().minusDays(1));
        garden.setMood(CharacterMood.CALM);

        CheerResponseDto response = gardenHomeService.cheer("minji", "gdn_3").getData();

        assertThat(response.getStreakDays()).isEqualTo(13);
        assertThat(response.getXpGained()).isEqualTo(10);
        assertThat(response.getTotalXp()).isEqualTo(1250);
        assertThat(response.getMood()).isEqualTo(CharacterMood.HAPPY);
        assertThat(garden.getLastCheeredOn()).isEqualTo(LocalDate.now());

        ArgumentCaptor<XpHistory> history = ArgumentCaptor.forClass(XpHistory.class);
        verify(xpHistoryRepository).save(history.capture());
        assertThat(history.getValue().getReason()).isEqualTo(XpReason.CHEER);
        assertThat(history.getValue().getAmount()).isEqualTo(10);
    }

    @Test
    void 하루를_건너뛰었으면_스트릭은_1일부터_다시_센다() {
        garden.setStreakDays(12);
        garden.setLastCheeredOn(LocalDate.now().minusDays(2));
        garden.setMood(CharacterMood.SAD);

        CheerResponseDto response = gardenHomeService.cheer("minji", "gdn_3").getData();

        assertThat(response.getStreakDays()).isEqualTo(1);
        assertThat(response.getMood()).isEqualTo(CharacterMood.HAPPY);
    }

    @Test
    void 응원은_하루에_한_번만_할_수_있다() {
        garden.setLastCheeredOn(LocalDate.now());

        assertThatThrownBy(() -> gardenHomeService.cheer("minji", "gdn_3")).isInstanceOf(CheerAlreadyDoneException.class);
        verify(xpHistoryRepository, never()).save(any());
        assertThat(garden.getTotalXp()).isEqualTo(1240);
    }
}
