package community.likelion.growwithusserver.care;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.care.service.TaskContextFactory;
import community.likelion.growwithusserver.care.service.TaskVisibility;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TaskPriorityIntegrationTest {
    @Test void existingTodayTasksExposeUrgentObservationBeforeRoutineAndCompletedTasks() {
        var date = LocalDate.of(2026, 11, 15);
        var guidance = List.of(new TaskRules.Guidance(TaskRules.Type.WATER, Set.of(Stage.FRUIT), 1,
                "물 주기", "검토된 작업", 10, "verified:test"));
        var garden = new CareGardenReader.Garden(1L, "care", "owner", "B-14", "tomato", "family",
                64, 119, Stage.FRUIT, null, guidance);
        var gardens = new CareGardenReader() {
            public List<Garden> findAssigned(String username) { return List.of(garden); }
            public List<Garden> findAll() { return List.of(garden); }
            public Optional<Garden> findGarden(Long id) { return id.equals(1L) ? Optional.of(garden) : Optional.empty(); }
            public Optional<Garden> findGardenForUpdate(Long id) { return findGarden(id); }
        };
        TaskContextFactory contexts = (found, day) -> new TaskRules.Context(day, Stage.FRUIT, true, false,
                guidance, List.of(), List.of("LEAF_SPOT"));
        var water = task(1L, TaskRules.Type.WATER, TaskStatus.TODO);
        var check = task(2L, TaskRules.Type.CHECK, TaskStatus.TODO);
        var completed = task(3L, TaskRules.Type.WEED, TaskStatus.DONE);
        var service = new TaskVisibility(gardens, contexts);
        var result = service.select("care", List.of(water, check, completed), date);
        assertEquals(List.of(2L, 1L, 3L), result.tasks().stream().map(Task::getId).toList());
        assertTrue(service.select("other-caretaker", List.of(water, check), date).tasks().isEmpty());
    }

    private Task task(Long id, TaskRules.Type type, TaskStatus status) {
        return Task.builder().id(id).gardenId(1L).caretakerUsername("care").type(type).status(status).minutes(5).build();
    }
}
