package community.likelion.growwithusserver.diary.service;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.pagination.*;
import community.likelion.growwithusserver.common.response.*;
import community.likelion.growwithusserver.common.time.DateTimes;
import community.likelion.growwithusserver.diary.data.entity.DiaryEntry;
import community.likelion.growwithusserver.diary.data.repository.DiaryRepository;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.List;

@org.springframework.context.annotation.Profile("local-demo")
@Service @RequiredArgsConstructor
public class LocalSnapshotDiaryService {
    private final CareGardenReader gardens;
    private final DiaryRepository entries;
    private final community.likelion.growwithusserver.care.data.repository.CareLogRepository careLogs;
    public enum Filter { ALL, PHOTO, STAGE }
    public record Item(String diaryId, DiaryEntry.Type type, LocalDate date, String careLogId, String eventId,
                       List<String> taskTypes, String transcript, String summary, String photoUrl, String caretakerName,
                       boolean hearted, Stage fromStage, Stage toStage, OffsetDateTime createdAt) {}

    @Transactional(readOnly = true)
    public GlobalResponse<CursorPageResponse<Item>> list(String username, String publicGardenId, Filter filter, CursorPageRequest request) {
        Long id = IdPrefix.GARDEN.parse(publicGardenId);
        var garden = gardens.findGarden(id).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (username == null || !username.equals(garden.ownerUsername()) || !id.equals(garden.gardenId())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        if (filter == null || request == null || request.getLimit() < 1 || request.getLimit() > CursorPageRequest.MAX_LIMIT) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        var fetched = entries.findPage(id, request.getCursorId(IdPrefix.DIARY),
                filter == Filter.STAGE ? DiaryEntry.Type.STAGE_CHANGE : null, filter == Filter.PHOTO,
                PageRequest.of(0, request.getFetchSize()));
        var page = CursorPageResponse.of(fetched, request.getLimit(), d -> IdPrefix.DIARY.format(d.getId()));
        var logIds = page.getItems().stream().filter(d -> d.getType() == DiaryEntry.Type.CARE_LOG).map(DiaryEntry::getSourceId).toList();
        var hearted = logIds.isEmpty() ? java.util.Set.<Long>of() : careLogs.findAllById(logIds).stream()
                .filter(log -> id.equals(log.getGardenId()) && log.getHeartsReceived() > 0)
                .map(log -> log.getId()).collect(java.util.stream.Collectors.toSet());
        return GlobalResponse.ok(page.map(d -> item(d, hearted.contains(d.getSourceId()))));
    }

    private Item item(DiaryEntry d, boolean hearted) {
        return new Item(IdPrefix.DIARY.format(d.getId()), d.getType(), d.getCreatedAt().toLocalDate(),
                d.getType() == DiaryEntry.Type.CARE_LOG ? IdPrefix.CARE_LOG.format(d.getSourceId()) : null,
                d.getType() == DiaryEntry.Type.STAGE_CHANGE ? IdPrefix.EVENT.format(d.getSourceId()) : null,
                d.getTaskTypes() == null || d.getTaskTypes().isBlank() ? List.of() : List.of(d.getTaskTypes().split(",")),
                d.getTranscript(), d.getSummary(), d.getPhotoUrl(), d.getCaretakerName(), d.getType() == DiaryEntry.Type.CARE_LOG && hearted,
                d.getFromStage(), d.getToStage(), DateTimes.toOffset(d.getCreatedAt()));
    }
}
