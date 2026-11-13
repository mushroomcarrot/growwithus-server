package community.likelion.growwithusserver.harvest.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.harvest.data.dto.VisitRequestDto;
import community.likelion.growwithusserver.harvest.data.dto.VisitResponseDto;
import community.likelion.growwithusserver.harvest.data.dto.VisitSlotResponseDto;
import community.likelion.growwithusserver.harvest.data.entity.Visit;
import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import community.likelion.growwithusserver.harvest.data.repository.VisitRepository;
import community.likelion.growwithusserver.harvest.data.exception.VisitAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.VisitAlreadyCanceledException;
import community.likelion.growwithusserver.harvest.data.exception.VisitAlreadyReservedException;
import community.likelion.growwithusserver.harvest.data.exception.VisitCancelDeadlinePassedException;
import community.likelion.growwithusserver.harvest.data.exception.VisitNotFoundException;
import community.likelion.growwithusserver.harvest.data.exception.VisitSlotNotBookableException;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import community.likelion.growwithusserver.harvest.garden.HarvestGardenReader;
import community.likelion.growwithusserver.harvest.service.VisitSlotPolicy.Slot;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.exception.UserNotFoundException;
import community.likelion.growwithusserver.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisitService {
    // 관리사가 방문 알림을 눌렀을 때 이동할 화면
    private static final String CARETAKER_SCREEN = "DT_CARE_TODAY";

    private final VisitRepository visitRepository;
    private final HarvestGardenService harvestGardenService;
    private final HarvestGardenReader harvestGardenReader;
    private final UserService userService;
    private final NotificationService notificationService;
    private final VisitCapacityGuard capacity;

    // 주말 슬롯과 예약 가능 여부. from, to를 생략하면 내일부터 2주다.
    @Transactional(readOnly = true)
    public GlobalResponse<List<VisitSlotResponseDto>> getVisitSlots(String username, String gardenId,
                                                                    LocalDate from, LocalDate to) {
        HarvestGarden garden = harvestGardenService.getOwnedGarden(username, gardenId);
        LocalDate today = LocalDate.now();

        LocalDate rangeFrom = from != null ? from : today.plusDays(1);
        LocalDate rangeTo = to != null ? to : rangeFrom.plusDays(VisitSlotPolicy.DEFAULT_RANGE_DAYS - 1L);
        if (rangeTo.isBefore(rangeFrom) || ChronoUnit.DAYS.between(rangeFrom, rangeTo) >= VisitSlotPolicy.MAX_RANGE_DAYS) {
            log.error("[getVisitSlots] 잘못된 조회 기간: {} ~ {}", rangeFrom, rangeTo);
            throw new VisitSlotNotBookableException();
        }

        Map<LocalDateTime, Long> reservedCounts = visitRepository
                .findAllByFarmIdAndVisitDateBetweenAndStatus(garden.getFarmId(), rangeFrom, rangeTo, VisitStatus.RESERVED).stream()
                .collect(Collectors.groupingBy(visit -> visit.getVisitDate().atTime(visit.getStartTime()), Collectors.counting()));

        List<VisitSlotResponseDto> slots = VisitSlotPolicy.slotsBetween(rangeFrom, rangeTo).stream()
                .map(slot -> VisitSlotResponseDto.builder()
                        .slotId(slot.slotId())
                        .date(slot.date())
                        .startTime(slot.startTimeText())
                        .endTime(slot.endTimeText())
                        .available(VisitSlotPolicy.isBookable(slot, today)
                                && reservedCounts.getOrDefault(slot.date().atTime(slot.startTime()), 0L) < VisitSlotPolicy.SLOT_CAPACITY)
                        .build())
                .toList();

        return GlobalResponse.ok(slots);
    }

    @Transactional
    public GlobalResponse<VisitResponseDto> reserve(String username, VisitRequestDto visitRequestDto) {
        HarvestGarden garden = harvestGardenService.getOwnedGarden(username, visitRequestDto.getGardenId());
        Slot slot = VisitSlotPolicy.parse(visitRequestDto.getSlotId());

        if (!VisitSlotPolicy.isBookable(slot, LocalDate.now())) {
            log.error("[reserve] 예약을 받지 않는 슬롯: {} ({})", username, slot.slotId());
            throw new VisitSlotNotBookableException();
        }
        if (visitRepository.existsByGardenIdAndVisitDateAndStatus(garden.getGardenId(), slot.date(), VisitStatus.RESERVED)) {
            log.error("[reserve] 같은 날 예약이 이미 있음: {} ({})", username, slot.date());
            throw new VisitAlreadyReservedException();
        }
        capacity.requireCapacity(garden.getFarmId(), slot);

        User owner = userService.getUserEntity(username);
        Visit visit = Visit.builder()
                .gardenId(garden.getGardenId())
                .farmId(garden.getFarmId())
                .user(owner)
                .visitDate(slot.date())
                .startTime(slot.startTime())
                .endTime(slot.endTime())
                .headcount(visitRequestDto.getHeadcount() == null ? Math.max(1, garden.getFamilySize()) : visitRequestDto.getHeadcount())
                .memo(visitRequestDto.getMemo())
                .status(VisitStatus.RESERVED)
                .reservedKey(Visit.reservedKeyOf(garden.getGardenId(), slot.date()))
                .createdAt(LocalDateTime.now())
                .build();

        try {
            visit = visitRepository.saveAndFlush(visit);
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 같은 날 예약은 reservedKey 유니크 제약이 막는다
            log.error("[reserve] 같은 날 예약이 동시에 들어옴: {} ({})", username, slot.date());
            throw new VisitAlreadyReservedException();
        }
        log.info("[reserve] 방문 예약: {} ({})", username, slot.slotId());

        notifyCaretaker(garden, visit, NotificationType.VISIT_RESERVED,
                KoreanParticle.subject(ownerLabel(garden, owner)) + " " + dateText(visit.getVisitDate()) + "에 방문해요",
                reservedBody(visit));

        return GlobalResponse.ok(VisitResponseDto.builder()
                .visitId(IdPrefix.VISIT.format(visit.getId()))
                .date(visit.getVisitDate())
                .startTime(slot.startTimeText())
                .status(visit.getStatus())
                .build());
    }

    // 방문 하루 전(전날 자정)까지만 취소할 수 있다.
    @Transactional
    public GlobalResponse<Void> cancel(String username, String visitId) {
        Visit visit = getVisitEntity(IdPrefix.VISIT.parse(visitId));
        User owner = visit.getUser();

        if (!username.equals(owner.getUsername())) {
            log.error("[cancel] 내 예약이 아닌 방문 취소 시도: {} ({})", username, visitId);
            throw new VisitAccessDeniedException();
        }
        if (visit.getStatus() == VisitStatus.CANCELED) {
            throw new VisitAlreadyCanceledException();
        }
        if (!LocalDate.now().isBefore(visit.getVisitDate())) {
            log.error("[cancel] 취소 기한이 지난 방문: {} ({})", username, visitId);
            throw new VisitCancelDeadlinePassedException();
        }

        visit.setStatus(VisitStatus.CANCELED);
        visit.setReservedKey(null);
        visit.setCanceledAt(LocalDateTime.now());
        visitRepository.save(visit);
        log.info("[cancel] 방문 예약 취소: {} ({})", username, visitId);

        // 텃밭이 사라졌어도 취소는 되어야 하므로 알림만 생략한다
        harvestGardenReader.findGarden(visit.getGardenId()).ifPresent(garden ->
                notifyCaretaker(garden, visit, NotificationType.VISIT_CANCELED,
                        ownerLabel(garden, owner) + " " + dateText(visit.getVisitDate()) + " 방문이 취소됐어요",
                        timeText(visit.getStartTime()) + " 방문이 취소됐어요"));

        return GlobalResponse.ok();
    }

    private Visit getVisitEntity(Long id) {
        return visitRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.error("[getVisitEntity] 방문 예약 조회 실패: {}", id);
                    return new VisitNotFoundException();
                });
    }

    // 담당 관리사가 없거나 계정을 찾지 못하면 알림 없이 넘어간다. 예약과 취소 자체는 그대로 처리한다.
    private void notifyCaretaker(HarvestGarden garden, Visit visit, NotificationType type, String title, String body) {
        if (garden.getCaretakerUsername() == null) {
            return;
        }

        User caretaker;
        try {
            caretaker = userService.getUserEntity(garden.getCaretakerUsername());
        } catch (UserNotFoundException e) {
            log.warn("[notifyCaretaker] 관리사 계정이 없어 알림 생략: {}", garden.getCaretakerUsername());
            return;
        }

        notificationService.send(NotificationSendDto.builder()
                .receiver(caretaker)
                .type(type)
                .title(title)
                .body(body)
                .targetScreen(CARETAKER_SCREEN)
                .targetId(IdPrefix.VISIT.format(visit.getId()))
                .build());
    }

    private String reservedBody(Visit visit) {
        String body = timeText(visit.getStartTime()) + "에 " + visit.getHeadcount() + "명이 와요";
        return visit.getMemo() == null || visit.getMemo().isBlank() ? body : body + ". " + visit.getMemo();
    }

    private String ownerLabel(HarvestGarden garden, User owner) {
        return garden.getOwnerNickname() != null ? garden.getOwnerNickname() : owner.getName() + "님";
    }

    // 예: 11월 28일
    private String dateText(LocalDate date) {
        return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    // 예: 오전 10시, 오후 2시
    private String timeText(LocalTime time) {
        int hour = time.getHour();
        return (hour < 12 ? "오전 " : "오후 ") + (hour > 12 ? hour - 12 : hour) + "시";
    }
}
