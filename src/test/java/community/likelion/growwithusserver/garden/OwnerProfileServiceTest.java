package community.likelion.growwithusserver.garden;

import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.farm.data.exception.PlotAlreadyAssignedException;
import community.likelion.growwithusserver.farm.data.exception.PlotNotFoundException;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileCreateResponseDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileRequestDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileResponseDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileUpdateRequestDto;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.exception.OwnerDistrictNotAllowedException;
import community.likelion.growwithusserver.garden.data.exception.OwnerProfileNotFoundException;
import community.likelion.growwithusserver.garden.data.exception.OwnerRoleRequiredException;
import community.likelion.growwithusserver.garden.data.exception.ProfileAlreadyExistsException;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.garden.service.OwnerProfileService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.NextStep;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerProfileServiceTest {
    @Test void storesFamilySizeAndReturnsItAfterRegistration() {
        when(plotRepository.findPlotForUpdate(1L,"B-14")).thenReturn(Optional.of(plot));
        var input=request(District.SUJI); input.setFamilySize(4);
        assertThat(ownerProfileService.register("minji",input).getData().getFamilySize()).isEqualTo(4);
        ArgumentCaptor<OwnerProfile> captured=ArgumentCaptor.forClass(OwnerProfile.class);
        verify(ownerProfileRepository).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getFamilySize()).isEqualTo(4);
    }

    @Mock
    private UserService userService;
    @Mock
    private OwnerProfileRepository ownerProfileRepository;
    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private PlotRepository plotRepository;

    private OwnerProfileService ownerProfileService;
    private User owner;
    private User caretaker;
    private Plot plot;

    @BeforeEach
    void setUp() {
        ownerProfileService = new OwnerProfileService(userService, ownerProfileRepository, gardenRepository, plotRepository);

        owner = User.builder().id(1L).username("minji").name("김민지").role(UserRole.OWNER).build();
        caretaker = User.builder().id(2L).username("soonja").name("박순자").role(UserRole.CARETAKER).build();
        Crop crop = Crop.builder().id(1L).name("방울토마토").characterName("토순이").baseTemp(10).build();
        plot = Plot.builder().id(5L).farm(Farm.builder().id(1L).build()).plotCode("B-14").crop(crop).caretaker(caretaker).build();

        lenient().when(userService.getUserEntity("minji")).thenReturn(owner);
        lenient().when(userService.resolveNextStep(owner)).thenAnswer(invocation ->
                owner.isProfileRegistered() ? NextStep.OWNER_HOME : NextStep.PROFILE_OWNER);
        lenient().when(gardenRepository.saveAndFlush(any(Garden.class))).thenAnswer(invocation -> {
            Garden garden = invocation.getArgument(0);
            garden.setId(3L);
            return garden;
        });
    }

    private OwnerProfileRequestDto request(District district) {
        return OwnerProfileRequestDto.builder().nickname(" 민지네 ").district(district).farmId("farm_1").plotCode("B-14").build();
    }

    @Test
    void 프로필을_등록하면_칸을_분양받고_씨앗_단계의_텃밭이_생긴다() {
        when(plotRepository.findPlotForUpdate(1L, "B-14")).thenReturn(Optional.of(plot));

        OwnerProfileCreateResponseDto response = ownerProfileService.register("minji", request(District.SUJI)).getData();

        assertThat(response.getGardenId()).isEqualTo("gdn_3");
        assertThat(response.getCharacterName()).isEqualTo("토순이");
        assertThat(response.getStage()).isEqualTo(GrowthStage.SEED);
        assertThat(response.getNextStep()).isEqualTo(NextStep.OWNER_HOME);
        assertThat(plot.isAssigned()).isTrue();
        assertThat(owner.isProfileRegistered()).isTrue();

        ArgumentCaptor<OwnerProfile> profile = ArgumentCaptor.forClass(OwnerProfile.class);
        verify(ownerProfileRepository).saveAndFlush(profile.capture());
        assertThat(profile.getValue().getNickname()).isEqualTo("민지네");

        ArgumentCaptor<Garden> garden = ArgumentCaptor.forClass(Garden.class);
        verify(gardenRepository).saveAndFlush(garden.capture());
        assertThat(garden.getValue().getCaretaker()).isEqualTo(caretaker);
        assertThat(garden.getValue().getStartedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void 이미_분양된_칸은_등록할_수_없다() {
        plot.setAssigned(true);
        when(plotRepository.findPlotForUpdate(1L, "B-14")).thenReturn(Optional.of(plot));

        assertThatThrownBy(() -> ownerProfileService.register("minji", request(District.SUJI)))
                .isInstanceOf(PlotAlreadyAssignedException.class);
        verify(gardenRepository, never()).saveAndFlush(any());
        assertThat(owner.isProfileRegistered()).isFalse();
    }

    @Test
    void 없는_칸은_등록할_수_없다() {
        when(plotRepository.findPlotForUpdate(1L, "B-14")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ownerProfileService.register("minji", request(District.SUJI)))
                .isInstanceOf(PlotNotFoundException.class);
    }

    @Test
    void 프로필이_이미_있으면_다시_등록할_수_없다() {
        when(ownerProfileRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> ownerProfileService.register("minji", request(District.SUJI)))
                .isInstanceOf(ProfileAlreadyExistsException.class);
        verify(plotRepository, never()).findPlotForUpdate(any(), any());
    }

    @Test
    void 처인구는_주인의_거주_구로_고를_수_없다() {
        assertThatThrownBy(() -> ownerProfileService.register("minji", request(District.CHEOIN)))
                .isInstanceOf(OwnerDistrictNotAllowedException.class);
    }

    @Test
    void 역할을_고르기_전에는_등록할_수_없다() {
        owner.setRole(UserRole.USER);

        assertThatThrownBy(() -> ownerProfileService.register("minji", request(District.SUJI)))
                .isInstanceOf(OwnerRoleRequiredException.class);
    }

    @Test
    void 보낸_필드만_수정한다() {
        OwnerProfile profile = OwnerProfile.builder().id(1L).user(owner).nickname("민지네").district(District.SUJI).build();
        when(ownerProfileRepository.findByUserUsername("minji")).thenReturn(Optional.of(profile));
        when(gardenRepository.findByOwnerUsername("minji")).thenReturn(Optional.of(Garden.builder().id(3L).build()));

        OwnerProfileResponseDto response = ownerProfileService
                .update("minji", OwnerProfileUpdateRequestDto.builder().nickname("민지와 아이들").build()).getData();

        assertThat(response.getNickname()).isEqualTo("민지와 아이들");
        assertThat(response.getDistrict()).isEqualTo(District.SUJI);
        assertThat(response.getGardenId()).isEqualTo("gdn_3");
    }

    @Test
    void 프로필이_없으면_수정할_수_없다() {
        when(ownerProfileRepository.findByUserUsername("minji")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ownerProfileService.update("minji", new OwnerProfileUpdateRequestDto()))
                .isInstanceOf(OwnerProfileNotFoundException.class);
    }
}
