package community.likelion.growwithusserver.diary;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules.Type;
import community.likelion.growwithusserver.care.data.entity.*;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.care.service.CareCompleted;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.diary.data.entity.DiaryEntry;
import community.likelion.growwithusserver.diary.data.repository.DiaryRepository;
import community.likelion.growwithusserver.diary.service.DiaryListener;
import community.likelion.growwithusserver.growth.data.entity.GrowthEvent;
import community.likelion.growwithusserver.growth.data.repository.GrowthEventRepository;
import community.likelion.growwithusserver.growth.service.GrowthChanged;
import community.likelion.growwithusserver.support.FakeRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiaryListenerTest {
    private final List<DiaryEntry> saved = new ArrayList<>();
    private boolean fail;
    private final DiaryRepository entries = FakeRepository.of(DiaryRepository.class, (method, args) -> {
        if (fail) throw new IllegalStateException("storage failed");
        if (method.equals("findByTypeAndSourceId")) return saved.stream().filter(e -> e.getType() == args[0] && e.getSourceId().equals(args[1])).findFirst();
        saved.add((DiaryEntry) args[0]); return args[0];
    });
    private final CareLog log = CareLog.builder().id(1L).gardenId(1L).taskId(2L).caretakerUsername("care")
            .transcript("original").summary("summary").photoUrl("http://localhost/photo.png").createdAt(LocalDateTime.now()).build();
    private DiaryListener listener() {
        return new DiaryListener(entries,
                FakeRepository.of(CareLogRepository.class, (m, a) -> Optional.of(log)),
                FakeRepository.of(TaskRepository.class, (m, a) -> Optional.of(Task.builder().type(Type.CHECK).build())),
                FakeRepository.of(CaretakerProfileRepository.class, (m, a) -> Optional.of(CaretakerProfile.builder().name("caretaker").build())),
                FakeRepository.of(GrowthEventRepository.class, (m, a) -> Optional.of(GrowthEvent.builder().id(5L).gardenId(1L)
                        .fromStage(Stage.SPROUT).toStage(Stage.FLOWER).summary("stage changed").createdAt(LocalDateTime.now()).build())));
    }
    @Test void snapshotsConfirmedCareLogWithoutReanalyzingText() {
        listener().onCare(new CareCompleted("owner", "summary", "cl_1"));
        var entry = saved.getFirst();
        assertEquals(DiaryEntry.Type.CARE_LOG, entry.getType()); assertEquals(1L, entry.getSourceId());
        assertEquals(log.getTranscript(), entry.getTranscript()); assertEquals(log.getSummary(), entry.getSummary());
        assertEquals(log.getPhotoUrl(), entry.getPhotoUrl()); assertEquals("CHECK", entry.getTaskTypes());
    }
    @Test void bundledTasksUpdateTheExistingEntryInsteadOfAddingOne() {
        listener().onCare(new CareCompleted("owner", "summary", "cl_1"));
        listener().onExtended(new community.likelion.growwithusserver.care.service.CareLogExtended(1L, List.of("CHECK", "WATER"), "both done"));
        assertEquals(1, saved.size());
        assertEquals("CHECK,WATER", saved.getFirst().getTaskTypes()); assertEquals("both done", saved.getFirst().getSummary());
    }
    @Test void addsStageChangeEntryAndPropagatesStorageFailure() {
        listener().onGrowth(new GrowthChanged("owner", "stage changed", "evt_5"));
        assertEquals(Stage.FLOWER, saved.getFirst().getToStage()); assertEquals(5L, saved.getFirst().getSourceId());
        fail = true;
        assertThrows(IllegalStateException.class, () -> listener().onCare(new CareCompleted("owner", "summary", "cl_1")));
    }
}
