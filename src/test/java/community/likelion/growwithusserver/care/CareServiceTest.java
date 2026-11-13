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
import community.likelion.growwithusserver.growth.service.GrowthStatusService;
import community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository;
import community.likelion.growwithusserver.voice.data.entity.VoiceMemo;
import community.likelion.growwithusserver.voice.data.repository.VoiceMemoRepository;
import community.likelion.growwithusserver.voice.service.VoiceTaskAccess;
import community.likelion.growwithusserver.voice.service.VoiceMemoStore;
import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CareServiceTest {
    @Test void photoUploadSurvivesUnavailableVisionAndThrownModelErrors() throws Exception {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        var saved = new ArrayList<TaskPhoto>();
        var photos = FakeRepository.of(TaskPhotoRepository.class, (method, args) -> {
            var photo = (TaskPhoto) args[0]; photo.setId((long) saved.size() + 1); saved.add(photo); return photo;
        });
        var writes = new TaskWriteService(access, tasks, photos, null, null, null, event -> {}, null);
        var uploads = new community.likelion.growwithusserver.common.file.FileUploadService(
                (directory, filename, input, size, type) -> "http://localhost/uploads/" + filename);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        for (boolean throwing : List.of(false, true)) {
            community.likelion.growwithusserver.common.ai.VisionClient unavailable = (image, task) -> {
                if (throwing) throw new community.likelion.growwithusserver.common.ai.AiClientException();
                return Optional.empty();
            };
            var service = new TaskPhotoService(access, uploads, unavailable, writes);
            var result = service.upload("care", "tsk_1", new org.springframework.mock.web.MockMultipartFile(
                    "photo", "public.png", "image/png", bytes.toByteArray())).getData();
            assertNotNull(result.getPhotoUrl()); assertNull(result.getAiCheck());
            assertNull(saved.getLast().getAiMatched()); assertNotNull(saved.getLast().getUrl());
        }
        assertEquals(2, saved.size());
    }
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
        var growth = new GrowthStatusService(FakeRepository.of(
                GrowthStateRepository.class, (method, args) -> Optional.empty()));
        var service = new TaskWriteService(access, tasks, photos, logs, null, new TaskVisibility(gardens, contexts), events::add, growth);
        var result = service.complete("care", 1L, null);
        assertEquals(TaskStatus.DONE, result.getStatus());
        assertEquals(0, result.getRemainingTasks()); assertEquals(1, records.size()); assertEquals(1, events.size());
        assertEquals("owner", ((CareCompleted) events.getFirst()).ownerUsername());
        assertThrows(VoiceMemoException.class, () -> service.complete("care", 1L, null));
        assertEquals(1, records.size()); assertEquals(1, events.size());
    }

    @Test void confirmedVoiceAndPhotoReachCareLogThroughRealTaskReader() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        var row = new java.util.concurrent.atomic.AtomicReference<VoiceMemo>();
        var memoRepo = FakeRepository.of(VoiceMemoRepository.class, (method, args) -> switch (method) {
            case "save" -> {
                var memo = (VoiceMemo) args[0];
                memo.setId(1L); row.set(memo); yield memo;
            }
            case "findById", "findForUpdate" -> Optional.ofNullable(row.get());
            case "findAccessById" -> Optional.of(new VoiceMemoRepository.Access() {
                public Long getTaskId() { return row.get().getTaskId(); }
                public String getCaretakerUsername() { return row.get().getCaretakerUsername(); }
            });
            default -> throw new AssertionError(method);
        });
        var voice = new VoiceMemoStore(memoRepo,
                new VoiceTaskAccess(new TaskVoiceReader(tasks, gardens)),
                new community.likelion.growwithusserver.common.file.FileUploadService((directory, filename, input, size, type) -> { throw new AssertionError("No upload expected"); }));
        var attempt = voice.createPending("care", 1L, "https://example.test/uploads/voice-memos/test.webm");
        voice.finish("care", attempt, new VoiceAnalysis.Result(
                "I watered and saw leaf spots.", "Watered today. We will watch the leaves.",
                List.of(VoiceAnalysis.Issue.LEAF_SPOT), false));
        voice.confirm("care", 1L, null);
        var photo = TaskPhoto.builder().id(1L).taskId(1L).caretakerUsername("care").url("https://example.test/photo.png").build();
        var photos = FakeRepository.of(TaskPhotoRepository.class, (method, args) -> Optional.of(photo));
        var records = new ArrayList<CareLog>(); var events = new ArrayList<Object>();
        var logs = FakeRepository.of(CareLogRepository.class, (method, args) -> {
            var log = (CareLog) args[0]; log.setId(1L); records.add(log); return log;
        });
        var growth = new GrowthStatusService(FakeRepository.of(
                GrowthStateRepository.class, (method, args) -> Optional.empty()));
        var service = new TaskWriteService(access, tasks, photos, logs, voice, new TaskVisibility(gardens, contexts), events::add, growth);
        var request = new TaskDtos.CompleteRequest("pht_1", "vm_1");
        service.complete("care", 1L, request);
        assertEquals(1, records.size());
        var log = records.getFirst();
        assertEquals(row.get().getTranscript(), log.getTranscript()); assertEquals(row.get().getSummary(), log.getSummary());
        assertEquals(1L, log.getVoiceMemoId()); assertEquals(photo.getUrl(), log.getPhotoUrl());
        assertEquals("owner", log.getOwnerUsername()); assertEquals(1, events.size());
        assertThrows(VoiceMemoException.class, () -> service.complete("care", 1L, request));
        assertEquals(1, records.size());
    }

    @Test void rejectsPhotoFromOtherTaskBeforeWritingCareLog() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        var photo = TaskPhoto.builder().id(2L).taskId(999L).caretakerUsername("care").build();
        var photos = FakeRepository.of(TaskPhotoRepository.class, (method, args) -> Optional.of(photo));
        var logs = FakeRepository.of(CareLogRepository.class, (method, args) -> { throw new AssertionError("must not save"); });
        var service = new TaskWriteService(access, tasks, photos, logs, null, new TaskVisibility(gardens, contexts),
                event -> fail("must not notify"), null);
        assertThrows(VoiceMemoException.class, () -> service.complete("care", 1L,
                new TaskDtos.CompleteRequest("pht_2", null)));
        assertEquals(TaskStatus.TODO, stored.getFirst().getStatus());
    }

    private TaskGenerationService generationWithoutModel() {
        var planner = new community.likelion.growwithusserver.ai.service.TaskPlanningService((system, input) -> {
            throw new AssertionError("repair must not invoke model");
        }, tools.jackson.databind.json.JsonMapper.builder().build());
        return new TaskGenerationService(tasks, gardens, contexts, planner, store());
    }

    @Test void addsOneObservationWhenWeatherHidesAllPendingWork() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        rain = true;
        assertEquals(1, generationWithoutModel().generate("care", LocalDate.now()).generated());
        assertEquals(2, stored.size());
        var visible = new TaskVisibility(gardens, contexts).select("care", stored, LocalDate.now());
        assertEquals(1, visible.tasks().size()); assertEquals(TaskRules.Type.CHECK, visible.tasks().getFirst().getType());
        assertEquals(0, generationWithoutModel().generate("care", LocalDate.now()).generated());
        assertEquals(2, stored.size());
        assertTrue(stored.stream().mapToInt(Task::getMinutes).sum() <= 120);
    }

    @Test void rechecksWeatherAtSaveAndDoesNotRepairAlreadyCompletedGarden() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        var observation = new TaskGenerationStore.Prepared(1L, List.of(TaskRules.Type.CHECK), true);
        assertEquals(0, store().save("care", LocalDate.now(), List.of(observation)).generated());
        assertEquals(1, stored.size());
        rain = true; stored.getFirst().setStatus(TaskStatus.DONE);
        assertEquals(0, generationWithoutModel().generate("care", LocalDate.now()).generated());
        assertEquals(1, stored.size());
    }

    @Test void repairKeepsSharedBudgetAndNeverAddsSecondObservation() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        rain = true; stored.getFirst().setMinutes(120);
        assertEquals(0, generationWithoutModel().generate("care", LocalDate.now()).generated());
        assertEquals(1, stored.size());
        stored.getFirst().setMinutes(70);
        generationWithoutModel().generate("care", LocalDate.now());
        stored.getLast().setStatus(TaskStatus.DONE);
        assertEquals(0, generationWithoutModel().generate("care", LocalDate.now()).generated());
        assertEquals(2, stored.size());
    }

    @Test void duplicateGardenInputDoesNotDuplicateObservationInSameBatch() {
        addGarden(1); store().save("care", LocalDate.now(), List.of(prepared(1)));
        rain = true;
        var observation = new TaskGenerationStore.Prepared(1L, List.of(TaskRules.Type.CHECK), true);
        assertEquals(1, store().save("care", LocalDate.now(), List.of(observation, observation)).generated());
        assertEquals(2, stored.size());
    }
}
