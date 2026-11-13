package community.likelion.growwithusserver.diary;

import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.diary.data.dto.DiaryItemDto;
import community.likelion.growwithusserver.diary.data.enums.DiaryFilter;
import community.likelion.growwithusserver.diary.data.enums.DiaryItemType;
import community.likelion.growwithusserver.diary.data.exception.DiaryCursorInvalidException;
import community.likelion.growwithusserver.diary.service.DiaryService;
import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretakerReader;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.GardenFixtures;
import community.likelion.growwithusserver.garden.care.GardenCareLog;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {
    @Test
    void preservesTranscriptAlongsideSummary() {
        when(gardenCareLogReader.findByGarden(eq(3L), any(), any())).thenReturn(List.of(
                new GardenCareLog(99L, 3L, "soonja", List.of("CHECK"), "summary", "photo", today.atTime(9, 0), "original")));
        CursorPageRequest page = new CursorPageRequest();
        var items = diaryService.getDiary("minji", "gdn_3", DiaryFilter.PHOTO, page).getData().getItems();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getTranscript()).isEqualTo("original");
        assertThat(items.getFirst().getSummary()).isEqualTo("summary");
    }

    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private GardenCareLogReader gardenCareLogReader;
    @Mock
    private EvolutionEventRepository evolutionEventRepository;
    @Mock
    private CareLogHeartRepository careLogHeartRepository;
    @Mock
    private FarmCaretakerReader farmCaretakerReader;

    private DiaryService diaryService;
    private Garden garden;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        diaryService = new DiaryService(new GardenService(gardenRepository), gardenCareLogReader, evolutionEventRepository,
                careLogHeartRepository, farmCaretakerReader);
        today = LocalDate.now();
        // 5일 전에 시작한 텃밭: 시작한 날, 4일 전, 3일 전, 2일 전, 어제, 오늘
        garden = GardenFixtures.garden();
        garden.setStartedOn(today.minusDays(5));

        lenient().when(gardenRepository.findDetail(3L)).thenReturn(Optional.of(garden));
        lenient().when(farmCaretakerReader.findByUsername("soonja")).thenReturn(Optional.empty());
        lenient().when(gardenCareLogReader.findByGarden(eq(3L), any(), any())).thenReturn(List.of(
                // 오늘 오전, 어제 두 건(사진 있음, 없음)
                new GardenCareLog(13L, 3L, "soonja", List.of("WATER"), "물을 줬어요", null, today.atTime(9, 0)),
                new GardenCareLog(12L, 3L, "soonja", List.of("SUPPORT", "PRUNE"), "지지대를 세웠어요", "http://photo",
                        today.minusDays(1).atTime(10, 20)),
                new GardenCareLog(11L, 3L, "soonja", List.of("WEED"), "풀을 뽑았어요", null, today.minusDays(1).atTime(8, 0))));
        lenient().when(evolutionEventRepository.findByGardenBetween(eq(3L), any(), any())).thenReturn(List.of(
                EvolutionEvent.builder().id(7L).garden(garden).fromStage(GrowthStage.SPROUT).toStage(GrowthStage.FLOWER)
                        .occurredAt(today.minusDays(3).atTime(6, 0)).build()));
        lenient().when(careLogHeartRepository.findHeartedCareLogIds(anyCollection())).thenReturn(List.of(12L));
    }

    private CursorPageRequest page(String cursor, Integer limit) {
        CursorPageRequest pageRequest = new CursorPageRequest();
        pageRequest.setCursor(cursor);
        pageRequest.setLimit(limit);
        return pageRequest;
    }

    @Test
    void 최신순으로_돌봄_기록과_단계_변화를_주고_빈_날은_쉬는_날로_채운다() {
        CursorPageResponse<DiaryItemDto> page = diaryService.getDiary("minji", "gdn_3", DiaryFilter.ALL, page(null, null)).getData();

        assertThat(page.getItems()).extracting(DiaryItemDto::getType).containsExactly(
                DiaryItemType.CARE_LOG,      // 오늘
                DiaryItemType.CARE_LOG,      // 어제 10:20
                DiaryItemType.CARE_LOG,      // 어제 08:00
                DiaryItemType.REST_DAY,      // 2일 전
                DiaryItemType.STAGE_CHANGE,  // 3일 전
                DiaryItemType.REST_DAY);     // 4일 전. 시작한 날은 채우지 않는다
        assertThat(page.getNextCursor()).isNull();

        DiaryItemDto careLog = page.getItems().get(1);
        assertThat(careLog.getCareLogId()).isEqualTo("cl_12");
        assertThat(careLog.getDate()).isEqualTo(today.minusDays(1));
        assertThat(careLog.getTaskTypes()).containsExactly("SUPPORT", "PRUNE");
        assertThat(careLog.getCaretakerName()).isEqualTo("박순자");
        assertThat(careLog.getHearted()).isTrue();
        assertThat(page.getItems().get(2).getHearted()).isFalse();

        DiaryItemDto stageChange = page.getItems().get(4);
        assertThat(stageChange.getEventId()).isEqualTo("evt_7");
        assertThat(stageChange.getFromStage()).isEqualTo(GrowthStage.SPROUT);
        assertThat(stageChange.getToStage()).isEqualTo(GrowthStage.FLOWER);

        assertThat(page.getItems().get(3).getMessage()).isEqualTo("오늘은 쉬는 날이었어요");
    }

    @Test
    void 하루를_나누지_않고_끊고_마지막_날짜를_커서로_준다() {
        CursorPageResponse<DiaryItemDto> first = diaryService.getDiary("minji", "gdn_3", DiaryFilter.ALL, page(null, 2)).getData();

        // 오늘 1건, 어제 2건. 어제를 다 담아 3건이 된다
        assertThat(first.getItems()).hasSize(3);
        assertThat(first.getNextCursor()).isEqualTo(today.minusDays(1).toString());

        CursorPageResponse<DiaryItemDto> second = diaryService
                .getDiary("minji", "gdn_3", DiaryFilter.ALL, page(first.getNextCursor(), 2)).getData();

        assertThat(second.getItems()).extracting(DiaryItemDto::getDate)
                .containsExactly(today.minusDays(2), today.minusDays(3));
        assertThat(second.getNextCursor()).isEqualTo(today.minusDays(3).toString());

        CursorPageResponse<DiaryItemDto> third = diaryService
                .getDiary("minji", "gdn_3", DiaryFilter.ALL, page(second.getNextCursor(), 2)).getData();

        assertThat(third.getItems()).extracting(DiaryItemDto::getDate).containsExactly(today.minusDays(4));
        assertThat(third.getNextCursor()).isNull();
    }

    @Test
    void 사진_필터는_사진이_있는_돌봄_기록만_준다() {
        CursorPageResponse<DiaryItemDto> page = diaryService.getDiary("minji", "gdn_3", DiaryFilter.PHOTO, page(null, null)).getData();

        assertThat(page.getItems()).extracting(DiaryItemDto::getCareLogId).containsExactly("cl_12");
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void 단계_필터는_단계_변화만_준다() {
        CursorPageResponse<DiaryItemDto> page = diaryService.getDiary("minji", "gdn_3", DiaryFilter.STAGE, page(null, 1)).getData();

        assertThat(page.getItems()).extracting(DiaryItemDto::getType).containsExactly(DiaryItemType.STAGE_CHANGE);
        // 더 오래된 단계 변화가 없으므로 다음 페이지가 없다
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void 시작한_날보다_앞의_커서는_빈_목록을_준다() {
        CursorPageResponse<DiaryItemDto> page = diaryService
                .getDiary("minji", "gdn_3", DiaryFilter.ALL, page(today.minusDays(5).toString(), null)).getData();

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void 날짜가_아닌_커서는_받지_않는다() {
        assertThatThrownBy(() -> diaryService.getDiary("minji", "gdn_3", DiaryFilter.ALL, page("cl_12", null)))
                .isInstanceOf(DiaryCursorInvalidException.class);
    }

    @Test
    void 남의_텃밭_일기는_볼_수_없다() {
        assertThatThrownBy(() -> diaryService.getDiary("other", "gdn_3", DiaryFilter.ALL, page(null, null)))
                .isInstanceOf(GardenAccessDeniedException.class);
    }
}
