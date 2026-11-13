package community.likelion.growwithusserver.farm;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;
import community.likelion.growwithusserver.farm.data.dto.PlotAdminRequestDto;
import community.likelion.growwithusserver.farm.data.dto.PlotAdminResponseDto;
import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.farm.garden.PlotGardenLink;
import community.likelion.growwithusserver.farm.service.FarmPlotAdminService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmPlotAdminServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private PlotRepository plotRepository;
    @Mock
    private CropRepository cropRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlotGardenLink plotGardenLink;

    private FarmPlotAdminService service;
    private Farm farm;
    private Crop tomato;
    private Crop lettuce;
    private User soonja;

    @BeforeEach
    void setUp() {
        service = new FarmPlotAdminService(farmRepository, plotRepository, cropRepository, userRepository, plotGardenLink);
        farm = Farm.builder().id(3L).name("양달농원").build();
        tomato = Crop.builder().id(1L).name("방울토마토").build();
        lettuce = Crop.builder().id(2L).name("상추").build();
        soonja = User.builder().id(9L).username("soonja").name("박순자").role(UserRole.CARETAKER).build();
        when(farmRepository.findById(3L)).thenReturn(Optional.of(farm));
        when(farmRepository.existsById(3L)).thenReturn(true);
        when(cropRepository.findById(1L)).thenReturn(Optional.of(tomato));
        when(cropRepository.findById(2L)).thenReturn(Optional.of(lettuce));
        when(userRepository.findByUsername("soonja")).thenReturn(Optional.of(soonja));
        when(plotRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GlobalException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void 없는_칸은_작물과_담당_관리사를_넣어_새로_만든다() {
        when(plotRepository.findPlotForUpdate(3L, "A-1")).thenReturn(Optional.empty());

        PlotAdminResponseDto created = service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_1", "soonja")).getData();

        assertThat(created.getFarmId()).isEqualTo("farm_3");
        assertThat(created.getPlotCode()).isEqualTo("A-1");
        assertThat(created.getCropId()).isEqualTo("crop_1");
        assertThat(created.getCaretakerUsername()).isEqualTo("soonja");
        assertThat(created.isAssigned()).isFalse();
    }

    @Test
    void 새_칸에_작물이_없거나_칸_번호가_잘못되면_C002다() {
        when(plotRepository.findPlotForUpdate(3L, "A-1")).thenReturn(Optional.empty());

        assertCode(() -> service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto(null, "soonja")), ErrorCode.INVALID_PARAMETER);
        for (String code : new String[]{"-A1", "A 1", "A/1", "A".repeat(21), ""}) {
            assertCode(() -> service.upsertPlot("farm_3", code, new PlotAdminRequestDto("crop_1", null)), ErrorCode.INVALID_PARAMETER);
        }
        verify(plotRepository, never()).saveAndFlush(any());
    }

    @Test
    void 농장_작물_회원이_없으면_404이고_관리사가_아닌_회원은_C002다() {
        when(farmRepository.findById(4L)).thenReturn(Optional.empty());
        when(cropRepository.findById(5L)).thenReturn(Optional.empty());
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("minji")).thenReturn(Optional.of(
                User.builder().id(1L).username("minji").role(UserRole.OWNER).build()));

        assertCode(() -> service.upsertPlot("farm_4", "A-1", new PlotAdminRequestDto("crop_1", null)), ErrorCode.DATA_NOT_FOUND);
        assertCode(() -> service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_5", null)), ErrorCode.DATA_NOT_FOUND);
        assertCode(() -> service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_1", "ghost")), ErrorCode.USER_DATA_NOT_FOUND);
        assertCode(() -> service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_1", "minji")), ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void 분양_전_칸은_보낸_값만_고친다() {
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("A-1").crop(tomato).build();
        when(plotRepository.findPlotForUpdate(3L, "A-1")).thenReturn(Optional.of(plot));

        service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto(null, "soonja"));
        assertThat(plot.getCrop()).isSameAs(tomato);
        assertThat(plot.getCaretaker()).isSameAs(soonja);

        service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_2", null));
        assertThat(plot.getCrop()).isSameAs(lettuce);
        assertThat(plot.getCaretaker()).isSameAs(soonja);
        verify(plotGardenLink, never()).assignCaretaker(any(), any());
    }

    @Test
    void 분양된_칸은_작물을_바꿀_수_없고_비어_있는_담당_관리사만_채운다() {
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("A-1").crop(tomato).assigned(true).build();
        when(plotRepository.findPlotForUpdate(3L, "A-1")).thenReturn(Optional.of(plot));

        assertCode(() -> service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_2", null)), ErrorCode.PLOT_ALREADY_ASSIGNED);

        service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto("crop_1", "soonja"));
        assertThat(plot.getCaretaker()).isSameAs(soonja);
        verify(plotGardenLink).assignCaretaker(5L, soonja);

        User other = User.builder().id(10L).username("malsoon").role(UserRole.CARETAKER).build();
        when(userRepository.findByUsername("malsoon")).thenReturn(Optional.of(other));
        assertCode(() -> service.upsertPlot("farm_3", "A-1", new PlotAdminRequestDto(null, "malsoon")), ErrorCode.PLOT_ALREADY_ASSIGNED);
        assertThat(plot.getCaretaker()).isSameAs(soonja);
    }

    @Test
    void 칸_목록은_없는_농장이면_404다() {
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("A-1").crop(tomato).caretaker(soonja).assigned(true).build();
        when(plotRepository.findAllByFarm(3L)).thenReturn(List.of(plot));

        List<PlotAdminResponseDto> plots = service.getPlots("farm_3").getData();
        assertThat(plots).hasSize(1);
        assertThat(plots.get(0).getCaretakerUsername()).isEqualTo("soonja");
        assertThat(plots.get(0).isAssigned()).isTrue();

        when(farmRepository.existsById(4L)).thenReturn(false);
        assertCode(() -> service.getPlots("farm_4"), ErrorCode.DATA_NOT_FOUND);
    }
}
