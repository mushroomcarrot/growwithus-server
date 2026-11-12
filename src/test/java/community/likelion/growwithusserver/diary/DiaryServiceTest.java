package community.likelion.growwithusserver.diary;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.care.data.entity.CareLog;
import community.likelion.growwithusserver.care.data.repository.CareLogRepository;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.response.GlobalException;
import community.likelion.growwithusserver.diary.data.entity.DiaryEntry;
import community.likelion.growwithusserver.diary.data.repository.DiaryRepository;
import community.likelion.growwithusserver.diary.service.DiaryService;
import community.likelion.growwithusserver.support.FakeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiaryServiceTest {
    private final List<DiaryEntry> rows = new ArrayList<>();
    private int reads;
    private final CareGardenReader gardens = new CareGardenReader() {
        public List<Garden> findAll() { return List.of(); }
        public List<Garden> findAssigned(String user) { return List.of(); }
        public Optional<Garden> findGarden(Long id) { return id.equals(1L) ? Optional.of(new Garden(1L, "care", "owner", "A", "crop", "family", 1, 1, Stage.SPROUT, null, List.of())) : Optional.empty(); }
        public Optional<Garden> findGardenForUpdate(Long id) { return findGarden(id); }
    };
    private final DiaryRepository entries = FakeRepository.of(DiaryRepository.class, (method, args) -> {
        if (!method.equals("findPage")) throw new AssertionError(method);
        reads++;
        return rows.stream().filter(d -> d.getGardenId().equals(args[0]) && (args[1] == null || d.getId() < (Long) args[1])
                && (args[2] == null || d.getType() == args[2]) && (!(Boolean) args[3] || d.getPhotoUrl() != null))
                .sorted(Comparator.comparing(DiaryEntry::getId).reversed()).limit(((Pageable) args[4]).getPageSize()).toList();
    });
    private final CareLogRepository logs = FakeRepository.of(CareLogRepository.class, (method, args) -> List.of(
            CareLog.builder().id(3L).gardenId(1L).heartsReceived(1).build()));
    private final DiaryService service = new DiaryService(gardens, entries, logs);
    private void populate() {
        for (long id = 1; id <= 3; id++) rows.add(DiaryEntry.builder().id(id).gardenId(1L).sourceId(id)
                .type(id == 2 ? DiaryEntry.Type.STAGE_CHANGE : DiaryEntry.Type.CARE_LOG).taskTypes(id == 2 ? null : "CHECK")
                .transcript(id == 2 ? null : "original " + id).summary("summary " + id).photoUrl(id == 3 ? "http://localhost/photo.png" : null)
                .createdAt(LocalDateTime.now()).build());
    }
    @Test void paginatesMixedEntriesWithoutLosingRows() {
        populate(); var request = new CursorPageRequest(); request.setLimit(2);
        var first = service.list("owner", "gdn_1", DiaryService.Filter.ALL, request).getData();
        assertEquals(List.of("dry_3", "dry_2"), first.getItems().stream().map(DiaryService.Item::diaryId).toList());
        assertEquals("dry_2", first.getNextCursor()); request.setCursor(first.getNextCursor());
        var last = service.list("owner", "gdn_1", DiaryService.Filter.ALL, request).getData();
        assertEquals("dry_1", last.getItems().getFirst().diaryId()); assertNull(last.getNextCursor());
    }
    @Test void keepsOriginalAndSummarySeparateAndReadsHeartStatus() {
        populate(); var item = service.list("owner", "gdn_1", DiaryService.Filter.ALL, new CursorPageRequest()).getData().getItems().getFirst();
        assertEquals("original 3", item.transcript()); assertEquals("summary 3", item.summary());
        assertEquals("cl_3", item.careLogId()); assertEquals(List.of("CHECK"), item.taskTypes()); assertTrue(item.hearted());
    }
    @Test void filtersPhotosAndStageChanges() {
        populate();
        assertEquals("dry_3", service.list("owner", "gdn_1", DiaryService.Filter.PHOTO, new CursorPageRequest()).getData().getItems().getFirst().diaryId());
        var stage = service.list("owner", "gdn_1", DiaryService.Filter.STAGE, new CursorPageRequest()).getData().getItems();
        assertEquals(1, stage.size()); assertEquals("evt_2", stage.getFirst().eventId()); assertNull(stage.getFirst().careLogId());
    }
    @Test void deniesOtherFamiliesBeforeReadingEntries() {
        assertThrows(GlobalException.class, () -> service.list("other", "gdn_1", DiaryService.Filter.ALL, new CursorPageRequest()));
        assertEquals(0, reads);
    }
    @Test void rejectsInvalidCursorAndLimits() {
        var request = new CursorPageRequest(); request.setCursor("cl_1");
        assertThrows(GlobalException.class, () -> service.list("owner", "gdn_1", DiaryService.Filter.ALL, request));
        request.setCursor(null); request.setLimit(0);
        assertThrows(GlobalException.class, () -> service.list("owner", "gdn_1", DiaryService.Filter.ALL, request));
        request.setLimit(51);
        assertThrows(GlobalException.class, () -> service.list("owner", "gdn_1", DiaryService.Filter.ALL, request));
        assertEquals(0, reads);
    }
    @Test void returnsEmptyPageForGardenWithoutEntries() {
        var page = service.list("owner", "gdn_1", DiaryService.Filter.ALL, new CursorPageRequest()).getData();
        assertTrue(page.getItems().isEmpty()); assertNull(page.getNextCursor());
    }
}
