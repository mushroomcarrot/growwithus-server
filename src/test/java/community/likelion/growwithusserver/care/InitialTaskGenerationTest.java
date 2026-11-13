package community.likelion.growwithusserver.care;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.service.TaskPlanningService;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.service.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class InitialTaskGenerationTest {
    @Test void initializesAssignedGardenThroughTheNormalGuardedStoreWithoutModelCalls() {
        var gardens=mock(CareGardenReader.class); var planner=mock(TaskPlanningService.class); var store=mock(TaskGenerationStore.class);
        var garden=new CareGardenReader.Garden(1L,"caretaker","owner","B14","crop","family",64,119,Stage.SEED,null,List.of());
        when(gardens.findGarden(1L)).thenReturn(Optional.of(garden));
        when(store.save(eq("caretaker"),eq(LocalDate.now()),any())).thenReturn(new TaskGenerationStore.Generated(1,1));
        var service=new TaskGenerationService(mock(TaskRepository.class),gardens,mock(TaskContextFactory.class),planner,store);
        assertEquals(1,service.generateInitial(1L).generated());
        verifyNoInteractions(planner);
        verify(store).save(eq("caretaker"),eq(LocalDate.now()),argThat(items -> items.size()==1 && items.getFirst().gardenId().equals(1L)));
    }
    @Test void unassignedGardenDoesNotCreateWorkForAnArbitraryCaretaker() {
        var gardens=mock(CareGardenReader.class); var store=mock(TaskGenerationStore.class);
        when(gardens.findGarden(1L)).thenReturn(Optional.of(new CareGardenReader.Garden(1L,null,"owner","B14","crop","family",64,119,Stage.SEED,null,List.of())));
        var service=new TaskGenerationService(mock(TaskRepository.class),gardens,mock(TaskContextFactory.class),mock(TaskPlanningService.class),store);
        assertEquals(0,service.generateInitial(1L).generated()); verifyNoInteractions(store);
    }
}
