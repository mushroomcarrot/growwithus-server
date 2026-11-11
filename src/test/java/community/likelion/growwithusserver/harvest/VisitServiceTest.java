package community.likelion.growwithusserver.harvest;

import community.likelion.growwithusserver.harvest.data.dto.VisitRequestDto;
import community.likelion.growwithusserver.harvest.data.dto.VisitResponseDto;
import community.likelion.growwithusserver.harvest.data.dto.VisitSlotResponseDto;
import community.likelion.growwithusserver.harvest.data.entity.Visit;
import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import community.likelion.growwithusserver.harvest.data.exception.VisitAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.VisitAlreadyCanceledException;
import community.likelion.growwithusserver.harvest.data.exception.VisitAlreadyReservedException;
import community.likelion.growwithusserver.harvest.data.exception.VisitCancelDeadlinePassedException;
import community.likelion.growwithusserver.harvest.data.exception.VisitSlotFullException;
import community.likelion.growwithusserver.harvest.data.exception.VisitSlotNotBookableException;
import community.likelion.growwithusserver.harvest.data.repository.VisitRepository;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import community.likelion.growwithusserver.harvest.garden.HarvestGardenReader;
import community.likelion.growwithusserver.harvest.service.HarvestGardenService;
import community.likelion.growwithusserver.harvest.service.VisitService;
import community.likelion.growwithusserver.harvest.service.VisitSlotPolicy;
import community.likelion.growwithusserver.harvest.service.VisitSlotPolicy.Slot;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {
    private static final Long GARDEN_ID = 3L;
    private static final Long FARM_ID = 1L;

    @Mock
    private VisitRepository visitRepository;
    @Mock
    private HarvestGardenReader harvestGardenReader;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    private VisitService visitService;
    private User owner;
    private User caretaker;
    private HarvestGarden garden;
    // 오늘 이후 첫 토요일 (오늘이 토요일이면 다음 주 토요일)
    private LocalDate saturday;
    private Slot slot;

    @BeforeEach
    void setUp() {
        visitService = new VisitService(visitRepository, new HarvestGardenService(harvestGardenReader),
                harvestGardenReader, userService, notificationService);

        owner = User.builder().id(1L).username("minji").name("김민지").build();
        caretaker = User.builder().id(2L).username("soonja").name("박순자").build();
        garden = HarvestGarden.builder()
                .gardenId(GARDEN_ID)
                .ownerUsername("minji")
                .ownerNickname("민지네")
                .farmId(FARM_ID)
                .caretakerUsername("soonja")
                .caretakerName("박순자")
                .build();
        saturday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        slot = new Slot(saturday, LocalTime.of(10, 0));

        lenient().when(harvestGardenReader.findGarden(GARDEN_ID)).thenReturn(Optional.of(garden));
        lenient().when(userService.getUserEntity("minji")).thenReturn(owner);
        lenient().when(userService.getUserEntity("soonja")).thenReturn(caretaker);
        lenient().when(visitRepository.saveAndFlush(any(Visit.class))).thenAnswer(invocation -> {
            Visit saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });
    }

    private VisitRequestDto request(Integer headcount, String memo) {
        return VisitRequestDto.builder().gardenId("gdn_3").slotId(slot.slotId()).headcount(headcount).memo(memo).build();
    }

    private Visit reservedVisit(LocalDate date) {
        return Visit.builder()
                .id(5L)
                .gardenId(GARDEN_ID)
                .farmId(FARM_ID)
                .user(owner)
                .visitDate(date)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(VisitStatus.RESERVED)
                .reservedKey(Visit.reservedKeyOf(GARDEN_ID, date))
                .build();
    }

    @Test
    void 예약하면_저장하고_관리사에게_알린다() {
        VisitResponseDto response = visitService.reserve("minji", request(3, "아이 둘이랑 가요")).getData();

        assertThat(response.getVisitId()).isEqualTo("vst_5");
        assertThat(response.getDate()).isEqualTo(saturday);
        assertThat(response.getStartTime()).isEqualTo("10:00");
        assertThat(response.getStatus()).isEqualTo(VisitStatus.RESERVED);

        ArgumentCaptor<Visit> visit = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).saveAndFlush(visit.capture());
        assertThat(visit.getValue().getHeadcount()).isEqualTo(3);
        assertThat(visit.getValue().getFarmId()).isEqualTo(FARM_ID);
        assertThat(visit.getValue().getEndTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(visit.getValue().getReservedKey()).isEqualTo("3:" + saturday);

        ArgumentCaptor<NotificationSendDto> notification = ArgumentCaptor.forClass(NotificationSendDto.class);
        verify(notificationService).send(notification.capture());
        assertThat(notification.getValue().getReceiver()).isSameAs(caretaker);
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.VISIT_RESERVED);
        assertThat(notification.getValue().getTitle())
                .isEqualTo("민지네가 " + saturday.getMonthValue() + "월 " + saturday.getDayOfMonth() + "일에 방문해요");
        assertThat(notification.getValue().getBody()).isEqualTo("오전 10시에 3명이 와요. 아이 둘이랑 가요");
        assertThat(notification.getValue().getTargetId()).isEqualTo("vst_5");
    }

    @Test
    void 인원을_생략하면_1명으로_저장한다() {
        visitService.reserve("minji", request(null, null));

        ArgumentCaptor<Visit> visit = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).saveAndFlush(visit.capture());
        assertThat(visit.getValue().getHeadcount()).isEqualTo(1);
    }

    @Test
    void 담당_관리사가_없으면_알림_없이_예약한다() {
        garden.setCaretakerUsername(null);

        assertThat(visitService.reserve("minji", request(null, null)).getData().getStatus()).isEqualTo(VisitStatus.RESERVED);
        verify(notificationService, never()).send(any());
    }

    @Test
    void 정원이_찬_슬롯은_C4096이다() {
        when(visitRepository.countByFarmIdAndVisitDateAndStartTimeAndStatus(FARM_ID, saturday, LocalTime.of(10, 0), VisitStatus.RESERVED))
                .thenReturn((long) VisitSlotPolicy.SLOT_CAPACITY);

        assertThatThrownBy(() -> visitService.reserve("minji", request(null, null)))
                .isInstanceOf(VisitSlotFullException.class);
        verify(visitRepository, never()).saveAndFlush(any());
    }

    @Test
    void 같은_날_예약이_있으면_C4095다() {
        when(visitRepository.existsByGardenIdAndVisitDateAndStatus(GARDEN_ID, saturday, VisitStatus.RESERVED)).thenReturn(true);

        assertThatThrownBy(() -> visitService.reserve("minji", request(null, null)))
                .isInstanceOf(VisitAlreadyReservedException.class);
    }

    @Test
    void 동시에_들어온_같은_날_예약은_유니크_제약으로_C4095가_된다() {
        when(visitRepository.saveAndFlush(any(Visit.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> visitService.reserve("minji", request(null, null)))
                .isInstanceOf(VisitAlreadyReservedException.class);
        verify(notificationService, never()).send(any());
    }

    @Test
    void 지난_주말_슬롯은_예약할_수_없다() {
        LocalDate lastSaturday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.SATURDAY));
        VisitRequestDto request = VisitRequestDto.builder()
                .gardenId("gdn_3")
                .slotId(new Slot(lastSaturday, LocalTime.of(10, 0)).slotId())
                .build();

        assertThatThrownBy(() -> visitService.reserve("minji", request))
                .isInstanceOf(VisitSlotNotBookableException.class);
    }

    @Test
    void 남의_텃밭은_C403_없는_텃밭은_C404다() {
        assertThatThrownBy(() -> visitService.reserve("other", request(null, null)))
                .isInstanceOf(GardenAccessDeniedException.class);

        when(harvestGardenReader.findGarden(GARDEN_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> visitService.reserve("minji", request(null, null)))
                .isInstanceOf(GardenNotFoundException.class);
    }

    @Test
    void 슬롯_목록은_정원이_찬_시간을_예약_불가로_준다() {
        Visit first = reservedVisit(saturday);
        Visit second = reservedVisit(saturday);
        when(visitRepository.findAllByFarmIdAndVisitDateBetweenAndStatus(FARM_ID, saturday, saturday.plusDays(1), VisitStatus.RESERVED))
                .thenReturn(List.of(first, second));

        List<VisitSlotResponseDto> slots = visitService
                .getVisitSlots("minji", "gdn_3", saturday, saturday.plusDays(1)).getData();

        assertThat(slots).hasSize(8);
        assertThat(slots.get(0).getSlotId()).isEqualTo(slot.slotId());
        assertThat(slots.get(0).isAvailable()).isFalse();
        assertThat(slots.subList(1, 8)).allMatch(VisitSlotResponseDto::isAvailable);
    }

    @Test
    void 기간을_생략하면_내일부터_2주를_준다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(visitRepository.findAllByFarmIdAndVisitDateBetweenAndStatus(FARM_ID, tomorrow, tomorrow.plusDays(13), VisitStatus.RESERVED))
                .thenReturn(List.of());

        List<VisitSlotResponseDto> slots = visitService.getVisitSlots("minji", "gdn_3", null, null).getData();

        // 14일에는 토요일과 일요일이 두 번씩 들어 있다
        assertThat(slots).hasSize(16);
        assertThat(slots).allMatch(VisitSlotResponseDto::isAvailable);
    }

    @Test
    void 조회_기간이_거꾸로거나_31일을_넘으면_C002다() {
        assertThatThrownBy(() -> visitService.getVisitSlots("minji", "gdn_3", saturday, saturday.minusDays(1)))
                .isInstanceOf(VisitSlotNotBookableException.class);
        assertThatThrownBy(() -> visitService.getVisitSlots("minji", "gdn_3", saturday, saturday.plusDays(31)))
                .isInstanceOf(VisitSlotNotBookableException.class);
    }

    @Test
    void 방문_전날까지는_취소하고_관리사에게_알린다() {
        Visit visit = reservedVisit(LocalDate.now().plusDays(1));
        when(visitRepository.findById(5L)).thenReturn(Optional.of(visit));

        visitService.cancel("minji", "vst_5");

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.CANCELED);
        assertThat(visit.getReservedKey()).isNull();
        assertThat(visit.getCanceledAt()).isNotNull();

        ArgumentCaptor<NotificationSendDto> notification = ArgumentCaptor.forClass(NotificationSendDto.class);
        verify(notificationService).send(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.VISIT_CANCELED);
        assertThat(notification.getValue().getReceiver()).isSameAs(caretaker);
        assertThat(notification.getValue().getTitle()).startsWith("민지네 ").endsWith(" 방문이 취소됐어요");
    }

    @Test
    void 방문_당일에는_취소할_수_없다() {
        Visit visit = reservedVisit(LocalDate.now());
        when(visitRepository.findById(5L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> visitService.cancel("minji", "vst_5"))
                .isInstanceOf(VisitCancelDeadlinePassedException.class);
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.RESERVED);
    }

    @Test
    void 남의_예약은_C403_이미_취소한_예약은_C4095다() {
        Visit visit = reservedVisit(LocalDate.now().plusDays(3));
        when(visitRepository.findById(5L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> visitService.cancel("other", "vst_5"))
                .isInstanceOf(VisitAccessDeniedException.class);

        visit.setStatus(VisitStatus.CANCELED);
        assertThatThrownBy(() -> visitService.cancel("minji", "vst_5"))
                .isInstanceOf(VisitAlreadyCanceledException.class);
    }
}
