package community.likelion.growwithusserver.diary.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.diary.data.dto.DiaryItemDto;
import community.likelion.growwithusserver.diary.data.enums.DiaryFilter;
import community.likelion.growwithusserver.diary.data.enums.DiaryItemType;
import community.likelion.growwithusserver.diary.data.exception.DiaryCursorInvalidException;
import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretaker;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretakerReader;
import community.likelion.growwithusserver.garden.care.GardenCareLog;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.user.data.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 성장 일기. 텃밭을 시작한 날부터 오늘까지를 날짜 단위로 최신순으로 내려준다.
 *
 * 1. 하루에 돌봄 기록과 단계 변화가 있으면 시각이 늦은 것부터 넣는다.
 * 2. 아무것도 없던 날은 REST_DAY 한 건으로 채운다 (ALL 필터만). 오늘과 텃밭을 시작한 날은 채우지 않는다.
 * 3. 커서는 날짜다. 하루를 페이지 사이에서 나누지 않으므로, 그 날을 다 담았을 때 limit을 넘으면 거기서 끊는다
 *    (그래서 한 페이지가 limit보다 조금 많을 수 있다). nextCursor는 이번 페이지에 담은 마지막 날짜다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiaryService {
    public static final String REST_DAY_MESSAGE = "오늘은 쉬는 날이었어요";

    private final GardenService gardenService;
    private final GardenCareLogReader gardenCareLogReader;
    private final EvolutionEventRepository evolutionEventRepository;
    private final CareLogHeartRepository careLogHeartRepository;
    private final FarmCaretakerReader farmCaretakerReader;

    @Transactional(readOnly = true)
    public GlobalResponse<CursorPageResponse<DiaryItemDto>> getDiary(String username, String gardenId, DiaryFilter filter,
                                                                     CursorPageRequest pageRequest) {
        Garden garden = gardenService.getOwnedGarden(username, gardenId);
        LocalDate today = LocalDate.now();
        LocalDate startedOn = garden.getStartedOn();

        // cursor(이전 페이지의 마지막 날짜)의 전날부터 본다
        LocalDate newest = parseCursor(pageRequest.getCursor());
        newest = newest == null || newest.isAfter(today) ? today : newest.minusDays(1);
        if (newest.isBefore(startedOn)) {
            return GlobalResponse.ok(new CursorPageResponse<>(List.of(), null));
        }

        List<Entry> entries = loadEntries(garden, filter, startedOn, newest);
        Map<LocalDate, List<Entry>> entriesByDate = new HashMap<>();
        for (Entry entry : entries) {
            entriesByDate.computeIfAbsent(entry.at().toLocalDate(), date -> new ArrayList<>()).add(entry);
        }

        List<DiaryItemDto> items = new ArrayList<>();
        String nextCursor = null;
        for (LocalDate date = newest; !date.isBefore(startedOn); date = date.minusDays(1)) {
            List<Entry> dayEntries = entriesByDate.getOrDefault(date, List.of());
            dayEntries.stream()
                    .sorted(Comparator.comparing(Entry::at).reversed())
                    .forEach(entry -> items.add(entry.item()));
            if (dayEntries.isEmpty() && filter == DiaryFilter.ALL && date.isBefore(today) && date.isAfter(startedOn)) {
                items.add(DiaryItemDto.builder().type(DiaryItemType.REST_DAY).date(date).message(REST_DAY_MESSAGE).build());
            }

            if (items.size() >= pageRequest.getLimit()) {
                if (hasOlder(filter, date, startedOn, entries)) {
                    nextCursor = date.toString();
                }
                break;
            }
        }

        fillHearted(items, entries);

        return GlobalResponse.ok(new CursorPageResponse<>(items, nextCursor));
    }

    // 필터에 맞는 돌봄 기록과 단계 변화를 기간 전체에서 한 번씩만 조회한다
    private List<Entry> loadEntries(Garden garden, DiaryFilter filter, LocalDate from, LocalDate to) {
        List<Entry> entries = new ArrayList<>();

        if (filter != DiaryFilter.STAGE) {
            Map<String, String> caretakerNames = new HashMap<>();
            for (GardenCareLog careLog : gardenCareLogReader.findByGarden(garden.getId(), from, to)) {
                if (filter == DiaryFilter.PHOTO && careLog.photoUrl() == null) {
                    continue;
                }
                entries.add(new Entry(careLog.createdAt(), careLog.careLogId(), DiaryItemDto.builder()
                        .type(DiaryItemType.CARE_LOG)
                        .date(careLog.createdAt().toLocalDate())
                        .careLogId(IdPrefix.CARE_LOG.format(careLog.careLogId()))
                        .taskTypes(careLog.taskTypes())
                        .summary(careLog.summary())
                        .photoUrl(careLog.photoUrl())
                        .caretakerName(caretakerNames.computeIfAbsent(careLog.caretakerUsername(),
                                caretakerUsername -> resolveCaretakerName(garden, caretakerUsername)))
                        .hearted(false)
                        .build()));
            }
        }

        if (filter != DiaryFilter.PHOTO) {
            List<EvolutionEvent> events = evolutionEventRepository.findByGardenBetween(garden.getId(),
                    from.atStartOfDay(), to.plusDays(1).atStartOfDay());
            for (EvolutionEvent event : events) {
                entries.add(new Entry(event.getOccurredAt(), null, DiaryItemDto.builder()
                        .type(DiaryItemType.STAGE_CHANGE)
                        .date(event.getOccurredAt().toLocalDate())
                        .eventId(IdPrefix.EVENT.format(event.getId()))
                        .fromStage(event.getFromStage())
                        .toStage(event.getToStage())
                        .build()));
            }
        }

        return entries;
    }

    // 이번 페이지의 마지막 날짜보다 앞에 더 보여줄 것이 있는지
    private boolean hasOlder(DiaryFilter filter, LocalDate lastDate, LocalDate startedOn, List<Entry> entries) {
        if (!lastDate.isAfter(startedOn)) {
            return false;
        }
        // ALL은 시작한 날을 뺀 모든 날에 항목이 있다 (없으면 쉬는 날). 시작한 날에는 기록이 있을 때만 항목이 있다
        if (filter == DiaryFilter.ALL && lastDate.minusDays(1).isAfter(startedOn)) {
            return true;
        }
        return entries.stream().anyMatch(entry -> entry.at().toLocalDate().isBefore(lastDate));
    }

    // 이번 페이지에 담긴 돌봄 기록의 하트 여부를 한 번에 조회해 채운다
    private void fillHearted(List<DiaryItemDto> items, List<Entry> entries) {
        Map<String, Long> careLogIds = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.careLogId() != null) {
                careLogIds.put(entry.item().getCareLogId(), entry.careLogId());
            }
        }

        List<Long> pageCareLogIds = items.stream()
                .filter(item -> item.getType() == DiaryItemType.CARE_LOG)
                .map(item -> careLogIds.get(item.getCareLogId()))
                .toList();
        if (pageCareLogIds.isEmpty()) {
            return;
        }

        Set<Long> hearted = new HashSet<>(careLogHeartRepository.findHeartedCareLogIds(pageCareLogIds));
        for (DiaryItemDto item : items) {
            if (item.getType() == DiaryItemType.CARE_LOG) {
                item.setHearted(hearted.contains(careLogIds.get(item.getCareLogId())));
            }
        }
    }

    // 관리사 프로필의 성함을 쓰고, 프로필이 없으면 담당 관리사의 회원 이름을 쓴다
    private String resolveCaretakerName(Garden garden, String caretakerUsername) {
        User caretaker = garden.getCaretaker();
        String fallback = caretaker != null && caretaker.getUsername().equals(caretakerUsername) ? caretaker.getName() : null;

        return farmCaretakerReader.findByUsername(caretakerUsername)
                .map(FarmCaretaker::name)
                .orElse(fallback);
    }

    private LocalDate parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(cursor);
        } catch (DateTimeParseException e) {
            log.error("[parseCursor] 일기 cursor 형식 오류: {}", cursor);
            throw new DiaryCursorInvalidException();
        }
    }

    // careLogId는 돌봄 기록일 때만 있다
    private record Entry(LocalDateTime at, Long careLogId, DiaryItemDto item) {
    }
}
