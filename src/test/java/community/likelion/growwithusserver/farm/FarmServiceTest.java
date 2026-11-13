package community.likelion.growwithusserver.farm;

import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretaker;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretakerReader;
import community.likelion.growwithusserver.farm.data.dto.FarmItemDto;
import community.likelion.growwithusserver.farm.data.dto.PlotPreviewResponseDto;
import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.exception.FarmNotFoundException;
import community.likelion.growwithusserver.farm.data.exception.PlotAlreadyAssignedException;
import community.likelion.growwithusserver.farm.data.exception.PlotNotFoundException;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.farm.service.FarmService;
import community.likelion.growwithusserver.user.data.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private PlotRepository plotRepository;
    @Mock
    private CropRepository cropRepository;
    @Mock
    private FarmCaretakerReader farmCaretakerReader;

    private FarmService farmService;
    private Farm farm;
    private Crop crop;

    @BeforeEach
    void setUp() {
        farmService = new FarmService(farmRepository, plotRepository, cropRepository, farmCaretakerReader);
        farm = Farm.builder().id(1L).name("처인 테마 주말농장").address("경기도 용인시 처인구")
                .latitude(37.2342).longitude(127.2017).district(District.CHEOIN).nx(64).ny(119).build();
        crop = Crop.builder().id(1L).name("방울토마토").characterName("토순이").baseTemp(10).build();
    }

    @Test
    void 농장_목록은_다음_페이지가_있으면_마지막_농장의_ID를_커서로_준다() {
        Farm second = Farm.builder().id(2L).name("양달농원").district(District.CHEOIN).latitude(37.1635).longitude(127.3151).build();
        Farm third = Farm.builder().id(3L).name("우리들").district(District.CHEOIN).latitude(37.1635).longitude(127.3151).build();
        when(farmRepository.findPage(eq(District.CHEOIN), isNull(), any())).thenReturn(List.of(farm, second, third));
        CursorPageRequest pageRequest = new CursorPageRequest();
        pageRequest.setLimit(2);

        CursorPageResponse<FarmItemDto> page = farmService.getFarms(District.CHEOIN, District.SUJI, pageRequest).getData();

        assertThat(page.getItems()).extracting(FarmItemDto::getFarmId).containsExactly("farm_1", "farm_2");
        assertThat(page.getNextCursor()).isEqualTo("farm_2");
        // 수지구 대표 좌표에서 처인구까지는 10km가 넘는다
        assertThat(page.getItems().get(0).getDistanceKm()).isBetween(10.0, 20.0);
    }

    @Test
    void 출발_구를_주지_않으면_거리는_비어_있다() {
        when(farmRepository.findPage(isNull(), isNull(), any())).thenReturn(List.of(farm));

        CursorPageResponse<FarmItemDto> page = farmService.getFarms(null, null, new CursorPageRequest()).getData();

        assertThat(page.getItems().get(0).getDistanceKm()).isNull();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void 칸_미리보기는_작물과_담당_관리사를_준다() {
        User caretaker = User.builder().id(2L).username("soonja").name("박순자").build();
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("B-14").crop(crop).caretaker(caretaker).build();
        when(farmRepository.existsById(1L)).thenReturn(true);
        when(plotRepository.findPlot(1L, "B-14")).thenReturn(Optional.of(plot));
        when(farmCaretakerReader.findByUsername("soonja")).thenReturn(Optional.of(new FarmCaretaker(7L, "순자 할머니", "http://avatar")));

        PlotPreviewResponseDto preview = farmService.getPlotPreview("farm_1", "B-14").getData();

        assertThat(preview.isAvailable()).isTrue();
        assertThat(preview.getCrop().getCropId()).isEqualTo("crop_1");
        assertThat(preview.getCrop().getCharacterName()).isEqualTo("토순이");
        assertThat(preview.getCaretaker().getCaretakerId()).isEqualTo("crt_7");
        assertThat(preview.getCaretaker().getName()).isEqualTo("순자 할머니");
        assertThat(preview.getMonthlyFee()).isEqualTo(30000);
    }

    @Test
    void 관리사_프로필이_없으면_회원_이름만_준다() {
        User caretaker = User.builder().id(2L).username("soonja").name("박순자").build();
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("B-14").crop(crop).caretaker(caretaker).build();
        when(farmRepository.existsById(1L)).thenReturn(true);
        when(plotRepository.findPlot(1L, "B-14")).thenReturn(Optional.of(plot));
        when(farmCaretakerReader.findByUsername("soonja")).thenReturn(Optional.empty());

        PlotPreviewResponseDto preview = farmService.getPlotPreview("farm_1", "B-14").getData();

        assertThat(preview.getCaretaker().getCaretakerId()).isNull();
        assertThat(preview.getCaretaker().getName()).isEqualTo("박순자");
    }

    @Test
    void 이미_분양된_칸은_미리볼_수_없다() {
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("B-14").crop(crop).assigned(true).build();
        when(farmRepository.existsById(1L)).thenReturn(true);
        when(plotRepository.findPlot(1L, "B-14")).thenReturn(Optional.of(plot));

        assertThatThrownBy(() -> farmService.getPlotPreview("farm_1", "B-14"))
                .isInstanceOf(PlotAlreadyAssignedException.class);
    }

    @Test
    void 없는_농장이나_칸은_찾을_수_없다() {
        when(farmRepository.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> farmService.getPlotPreview("farm_9", "B-14")).isInstanceOf(FarmNotFoundException.class);

        when(farmRepository.existsById(1L)).thenReturn(true);
        when(plotRepository.findPlot(1L, "Z-99")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> farmService.getPlotPreview("farm_1", "Z-99")).isInstanceOf(PlotNotFoundException.class);
    }
}
