package community.likelion.growwithusserver.care;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.entity.*;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.care.service.*;
import community.likelion.growwithusserver.support.FakeRepository;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CareServiceTest {
    private final List<Task> stored = new ArrayList<>();
    private final Map<Long, CareGardenReader.Garden> assigned = new LinkedHashMap<>();
    private boolean rain;
    private final CareGardenReader gardens = new CareGardenReader() {
        public List<Garden> findAssigned(String name) { return assigned.values().stream().filter(g -> name.equals(g.caretakerUsername())).toList(); }
        public List<Garden> findAll() { return List.copyOf(assigned.values()); }
        public Optional<Garden> findGarden(Long id) { return Optional.ofNullable(assigned.get(id)); }
        public Optional<Garden> findGardenForUpdate(Long id) { return findGarden(id); }
    };
    private final TaskRepository tasks = FakeRepository.of(TaskRepository.class, (method, args) -> switch (method) {
        case "save" -> { var t = (Task) args[0]; t.setId((long) stored.size() + 1); stored.add(t); yield t; }
        case "findById", "findForUpdate" -> stored.stream().filter(t -> t.getId().equals(args[0])).findFirst();
        case "findAllByCaretakerUsernameAndDateOrderByIdAsc" -> stored.stream().filter(t -> t.getCaretakerUsername().equals(args[0]) && t.getDate().equals(args[1])).toList();
        case "existsByGardenIdAndDate" -> stored.stream().anyMatch(t -> t.getGardenId().equals(args[0]) && t.getDate().equals(args[1]));
        default -> throw new AssertionError(method);
    });
    private final TaskContextFactory contexts = (garden, date) -> new TaskRules.Context(date, garden.stage(), true, rain, garden.guidance(), List.of());
    private final TaskAccess access = new TaskAccess(tasks, gardens);

    private void addGarden(long id) {
        var water = new TaskRules.Guidance(TaskRules.Type.WATER, Set.of(Stage.SPROUT), 1, "water", "check soil first", 70, "verified:test");
        assigned.put(id, new CareGardenReader.Garden(id, "care", "owner", "A1", "crop", "family", 1, 1, Stage.SPROUT, null, List.of(water)));
    }
    private TaskGenerationStore store() {
        var days = FakeRepository.of(TaskDayRepository.class, (method, args) -> switch (method) {
            case "ensureDay" -> null;
            case "lockDay" -> Optional.of(new TaskDay());
            default -> throw new AssertionError(method);
        });
        return new TaskGenerationStore(tasks, days, access, contexts);
    }
    private TaskGenerationStore.Prepared prepared(long id) {
        return new TaskGenerationStore.Prepared(id, List.of(TaskRules.Type.WATER), false);
    }
    @Test void capsTotalAcrossGardensAndRepeatsWithoutDuplicates() {
        addGarden(1); addGarden(2);
        var input = List.of(prepared(1), prepared(2));
        store().save("care", LocalDate.now(), input);
        assertTrue(stored.stream().mapToInt(Task::getMinutes).sum() <= 120);
        assertEquals(2, stored.size());
        assertEquals(TaskRules.Type.CHECK, stored.get(1).getType());
        store().save("care", LocalDate.now(), input);
        assertEquals(2, stored.size());
    }
    @Test void revalidatesWeatherBeforeSavingAndHidesStaleWatering() {
        addGarden(1);
        store().save("care", LocalDate.now(), List.of(prepared(1)));
        rain = true;
        var selected = new TaskVisibility(gardens, contexts).select("care", stored, LocalDate.now());
        assertTrue(selected.tasks().isEmpty());
        assertTrue(selected.skippedTypes().contains(TaskRules.Type.WATER));
        stored.clear();
        store().save("care", LocalDate.now(), List.of(prepared(1)));
        assertEquals(TaskRules.Type.CHECK, stored.getFirst().getType());
    }
    @Test void rejectsOtherCaretakersAndAlreadyCompletedTasks() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        assertThrows(VoiceMemoException.class, () -> access.open("other", 1L, true));
        stored.getFirst().setStatus(TaskStatus.DONE);
        assertThrows(VoiceMemoException.class, () -> access.open("care", 1L, true));
    }
    @Test void keepsCompletedCardsVisibleWhenWeatherChanges() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        stored.getFirst().setStatus(TaskStatus.DONE); rain = true;
        assertEquals(1, new TaskVisibility(gardens, contexts).select("care", stored, LocalDate.now()).tasks().size());
    }
    @Test void completionCreatesOneRecordAndNotificationEvent() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        var records = new ArrayList<CareLog>(); var events = new ArrayList<Object>();
        var logs = FakeRepository.of(CareLogRepository.class, (method, args) -> {
            if (!method.equals("save")) throw new AssertionError(method);
            var log = (CareLog) args[0]; log.setId(1L); records.add(log); return log;
        });
        var photos = FakeRepository.of(TaskPhotoRepository.class, (method, args) -> Optional.empty());
        var service = new TaskWriteService(access, tasks, photos, logs, null, new TaskVisibility(gardens, contexts), events::add);
        var result = service.complete("care", 1L, null);
        assertEquals(TaskStatus.DONE, result.getStatus());
        assertEquals(0, result.getRemainingTasks()); assertEquals(1, records.size()); assertEquals(1, events.size());
        assertEquals("owner", ((CareCompleted) events.getFirst()).ownerUsername());
        assertThrows(VoiceMemoException.class, () -> service.complete("care", 1L, null));
        assertEquals(1, records.size()); assertEquals(1, events.size());
    }
}
