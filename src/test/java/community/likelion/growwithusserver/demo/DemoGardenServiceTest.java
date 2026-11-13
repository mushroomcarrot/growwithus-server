package community.likelion.growwithusserver.demo;

import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.service.*;
import community.likelion.growwithusserver.demo.data.dto.DemoStateResponseDto;
import community.likelion.growwithusserver.demo.service.DemoGardenService;
import community.likelion.growwithusserver.farm.data.entity.*;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DemoGardenServiceTest {
    private final GardenRepository gardens = mock(GardenRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskGenerationService generation = mock(TaskGenerationService.class);
    private final TaskVisibility visibility = mock(TaskVisibility.class);
    private final DemoGardenService service = new DemoGardenService(gardens, tasks, generation, visibility, mock(community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository.class));

    private Garden garden(String externalId) {
        return Garden.builder().id(1L).stage(GrowthStage.FLOWER)
                .caretaker(User.builder().username("soonja").build())
                .plot(Plot.builder().plotCode(FarmSeeder.DEMO_PLOT_CODE)
                        .farm(Farm.builder().externalId(externalId).build()).build()).build();
    }

    @Test
    void 시연_계정이_실제_농장을_쓰면_변경하지_않는다() {
        when(gardens.findByOwnerUsername("minji")).thenReturn(Optional.of(garden("real-farm")));
        assertThat(service.refreshTasks().generated()).isZero();
        var state = new DemoStateResponseDto();
        service.populate(state);
        assertThat(state.getStage()).isNull();
        verifyNoInteractions(tasks, generation, visibility);
    }

    @Test
    void 시연_텃밭이_없어도_빈_상태를_반환한다() {
        when(gardens.findByOwnerUsername("minji")).thenReturn(Optional.empty());
        assertThat(service.refreshTasks().generated()).isZero();
        verifyNoInteractions(tasks, generation, visibility);
    }

    @Test
    void 상태_조회는_작업을_만들지_않고_시연_텃밭만_집계한다() {
        when(gardens.findByOwnerUsername("minji")).thenReturn(Optional.of(garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID)));
        Task own = Task.builder().gardenId(1L).status(TaskStatus.TODO).build();
        Task other = Task.builder().gardenId(2L).status(TaskStatus.TODO).build();
        when(tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc("soonja", LocalDate.now()))
                .thenReturn(List.of(own, other));
        when(visibility.select("soonja", List.of(own), LocalDate.now()))
                .thenReturn(new TaskVisibility.Visible(List.of(own), Set.of(), "안내"));
        var state = new DemoStateResponseDto();
        service.populate(state);
        assertThat(state.getStage()).isEqualTo("FLOWER");
        assertThat(state.getStageProgress()).isEqualTo("3/4");
        assertThat(state.getTodayTaskGardens()).isEqualTo(1);
        verifyNoInteractions(generation);
    }

    @Test
    void 날씨_변경은_기존_생성기를_호출하고_현재_안내를_반환한다() {
        when(gardens.findByOwnerUsername("minji")).thenReturn(Optional.of(garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID)));
        when(generation.generateInitial(1L)).thenReturn(new TaskGenerationStore.Generated(1, 1));
        when(tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc("soonja", LocalDate.now())).thenReturn(List.of());
        when(visibility.select("soonja", List.of(), LocalDate.now()))
                .thenReturn(new TaskVisibility.Visible(List.of(), Set.of(), "비 안내"));
        assertThat(service.refreshTasks()).isEqualTo(new DemoGardenService.Refreshed("비 안내", 1));
        verify(generation).generateInitial(1L);
        verify(tasks, never()).deleteAll();
    }
}
