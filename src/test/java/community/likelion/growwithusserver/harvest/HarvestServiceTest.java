package community.likelion.growwithusserver.harvest;

import community.likelion.growwithusserver.harvest.data.dto.HarvestResponseDto;
import community.likelion.growwithusserver.harvest.data.dto.OutingResponseDto;
import community.likelion.growwithusserver.harvest.data.entity.Outing;
import community.likelion.growwithusserver.harvest.data.enums.OutingType;
import community.likelion.growwithusserver.harvest.data.repository.OutingRepository;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import community.likelion.growwithusserver.harvest.garden.HarvestGardenReader;
import community.likelion.growwithusserver.harvest.service.HarvestGardenService;
import community.likelion.growwithusserver.harvest.service.HarvestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarvestServiceTest {
    @Mock
    private HarvestGardenReader harvestGardenReader;
    @Mock
    private OutingRepository outingRepository;

    private HarvestService harvestService;
    private HarvestGarden garden;

    @BeforeEach
    void setUp() {
        var growth = new community.likelion.growwithusserver.growth.service.GrowthStatusService(
                community.likelion.growwithusserver.support.FakeRepository.of(
                        community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository.class,
                        (method, args) -> Optional.empty()));
        harvestService = new HarvestService(new HarvestGardenService(harvestGardenReader), outingRepository, growth,
                org.mockito.Mockito.mock(community.likelion.growwithusserver.harvest.data.repository.VisitRepository.class));
        garden = HarvestGarden.builder()
                .gardenId(3L)
                .ownerUsername("minji")
                .characterName("토순이")
                .farmId(1L)
                .caretakerName("박순자")
                .startedOn(LocalDate.now().minusDays(45))
                .expectedHarvestDate(LocalDate.now().plusDays(18))
                .careLogCount(21)
                .build();
        when(harvestGardenReader.findGarden(3L)).thenReturn(Optional.of(garden));
    }

    @Test
    void 수확_안내는_남은_일수와_함께_돌본_일수를_준다() {
        HarvestResponseDto response = harvestService.getHarvest("minji", "gdn_3").getData();
        LocalDate expected = garden.getExpectedHarvestDate();

        assertThat(response.getExpectedDate()).isEqualTo(expected);
        assertThat(response.getDDay()).isEqualTo(18);
        assertThat(response.isHarvestable()).isFalse();
        assertThat(response.getDaysCaredTogether()).isEqualTo(46);
        assertThat(response.getCareLogCount()).isEqualTo(21);
        assertThat(response.getCaretakerName()).isEqualTo("박순자");
        assertThat(response.getMessage())
                .isEqualTo(expected.getMonthValue() + "월 " + expected.getDayOfMonth() + "일쯤 토순이를 만나러 오세요");
    }

    @Test
    void 수확일이_지났으면_dDay는_0이고_수확할_수_있으면_문구가_바뀐다() {
        garden.setExpectedHarvestDate(LocalDate.now().minusDays(2));
        garden.setHarvestable(true);

        HarvestResponseDto response = harvestService.getHarvest("minji", "gdn_3").getData();

        assertThat(response.getDDay()).isZero();
        assertThat(response.isHarvestable()).isTrue();
        assertThat(response.getMessage()).isEqualTo("토순이가 다 자랐어요. 주말에 만나러 오세요");
    }

    @Test
    void 수확일이_아직_없으면_dDay는_null이고_받침에_맞는_조사를_쓴다() {
        garden.setExpectedHarvestDate(null);
        garden.setCharacterName(null);

        HarvestResponseDto response = harvestService.getHarvest("minji", "gdn_3").getData();

        assertThat(response.getExpectedDate()).isNull();
        assertThat(response.getDDay()).isNull();
        assertThat(response.getMessage()).isEqualTo("작물이 자라는 중이에요. 수확일을 곧 알려드릴게요");
    }

    @Test
    void 나들이는_농장에서_가까운_순으로_준다() {
        garden.setFarmLatitude(37.15);
        garden.setFarmLongitude(127.30);
        when(outingRepository.findAll()).thenReturn(List.of(
                Outing.builder().type(OutingType.RESTAURANT).name("먼 식당").latitude(37.25).longitude(127.30).build(),
                Outing.builder().type(OutingType.EXPERIENCE_FARM).name("가까운 농장").latitude(37.16).longitude(127.30).build()
        ));

        List<OutingResponseDto> outings = harvestService.getOutings("minji", "gdn_3").getData();

        assertThat(outings).extracting(OutingResponseDto::getName).containsExactly("가까운 농장", "먼 식당");
        // 위도 0.01도는 약 1.1km
        assertThat(outings.get(0).getDistanceKm()).isEqualTo(1.1);
        assertThat(outings.get(1).getDistanceKm()).isEqualTo(11.1);
    }

    @Test
    void 농장_위치를_모르면_거리_없이_등록_순으로_준다() {
        when(outingRepository.findAll()).thenReturn(List.of(
                Outing.builder().type(OutingType.RESTAURANT).name("첫째").latitude(37.25).longitude(127.30).build(),
                Outing.builder().type(OutingType.EXPERIENCE_FARM).name("둘째").latitude(37.16).longitude(127.30).build()
        ));

        List<OutingResponseDto> outings = harvestService.getOutings("minji", "gdn_3").getData();

        assertThat(outings).extracting(OutingResponseDto::getName).containsExactly("첫째", "둘째");
        assertThat(outings).allMatch(outing -> outing.getDistanceKm() == null);
    }

    @Test void 수확_안내는_수확_확인과_별개로_현재_단계를_준다() {
        garden.setStage(community.likelion.growwithusserver.farm.data.enums.GrowthStage.FRUIT);
        var response = harvestService.getHarvest("minji", "gdn_3").getData();
        assertThat(response.getStage()).isEqualTo(garden.getStage());
        assertThat(response.isHarvestable()).isFalse();
    }

}
