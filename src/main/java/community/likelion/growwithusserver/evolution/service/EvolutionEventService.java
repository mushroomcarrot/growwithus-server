package community.likelion.growwithusserver.evolution.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.evolution.data.dto.EventAckResponseDto;
import community.likelion.growwithusserver.evolution.data.dto.EvolutionEventResponseDto;
import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.evolution.data.exception.EvolutionEventAlreadyAckedException;
import community.likelion.growwithusserver.evolution.data.exception.EvolutionEventNotFoundException;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.service.BadgeService;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionEventService {
    private final EvolutionEventRepository evolutionEventRepository;
    private final GardenService gardenService;
    private final XpService xpService;
    private final BadgeService badgeService;
    private final NotificationService notificationService;

    /**
     * 텃밭의 단계를 올리고 진화 이벤트를 만든 뒤 주인에게 알린다. 성장 시뮬레이션, 관리사의 단계 확인,
     * 시연의 빨리 감기가 단계를 올릴 때 이 메서드를 부른다. 호출한 쪽의 트랜잭션에 참여한다.
     *
     * @return 만든 이벤트. 새 단계가 지금 단계보다 높지 않으면 아무것도 하지 않고 비어 있다
     */
    @Transactional
    public Optional<EvolutionEvent> advanceStage(Long gardenId, GrowthStage newStage) {
        Garden garden = gardenService.getGardenForUpdate(gardenId);
        GrowthStage fromStage = garden.getStage();
        if (newStage.getLevel() <= fromStage.getLevel()) {
            return Optional.empty();
        }

        garden.setStage(newStage);
        EvolutionEvent event = evolutionEventRepository.save(EvolutionEvent.builder()
                .garden(garden)
                .fromStage(fromStage)
                .toStage(newStage)
                .occurredAt(LocalDateTime.now())
                .rewardXp(rewardXp(newStage))
                .rewardBadgeCode(rewardBadge(newStage).map(Enum::name).orElse(null))
                .build());
        log.info("[advanceStage] 단계 변경: 텃밭 {} ({} > {})", gardenId, fromStage, newStage);

        notificationService.send(NotificationSendDto.builder()
                .receiver(garden.getOwner())
                .type(NotificationType.STAGE_UP)
                .title(garden.getCharacterName() + "의 모습이 달라졌어요")
                .body("지금 확인하고 선물을 받아 보세요")
                .targetScreen("DT_EVOLVE")
                .targetId(IdPrefix.EVENT.format(event.getId()))
                .build());

        return Optional.of(event);
    }

    @Transactional(readOnly = true)
    public GlobalResponse<List<EvolutionEventResponseDto>> getUnackedEvents(String username, String gardenId) {
        Garden garden = gardenService.getOwnedGarden(username, gardenId);

        return GlobalResponse.ok(evolutionEventRepository.findAllByGardenIdAndAckedFalseOrderByIdAsc(garden.getId()).stream()
                .map(EvolutionEventResponseDto::from)
                .toList());
    }

    // 이벤트를 확인하고 보상(XP, 배지)을 준다. 이벤트 행을 잠그므로 동시에 눌러도 보상은 한 번만 나간다
    @Transactional
    public GlobalResponse<EventAckResponseDto> ack(String username, String eventId) {
        EvolutionEvent event = evolutionEventRepository.findByIdForUpdate(IdPrefix.EVENT.parse(eventId))
                .orElseThrow(() -> {
                    log.error("[ack] 진화 이벤트 조회 실패: {}", eventId);
                    return new EvolutionEventNotFoundException();
                });
        Garden garden = gardenService.getGardenForUpdate(event.getGarden().getId());
        gardenService.requireOwner(username, garden);
        if (event.isAcked()) {
            log.error("[ack] 이미 확인한 진화 이벤트: {}", eventId);
            throw new EvolutionEventAlreadyAckedException();
        }

        event.setAcked(true);
        event.setAckedAt(LocalDateTime.now());
        int totalXp = xpService.grant(garden, XpReason.EVENT, event.getRewardXp());
        EventAckResponseDto.BadgeDto badge = event.getRewardBadgeCode() == null
                ? null
                : badgeService.grant(garden.getOwner(), BadgeCode.valueOf(event.getRewardBadgeCode()))
                        .map(granted -> new EventAckResponseDto.BadgeDto(granted.getCode(), granted.getName()))
                        .orElse(null);

        return GlobalResponse.ok(EventAckResponseDto.builder()
                .eventId(eventId)
                .xpGained(event.getRewardXp())
                .totalXp(totalXp)
                .badge(badge)
                .build());
    }

    // 단계가 오를수록 보상이 크다
    public static int rewardXp(GrowthStage toStage) {
        return switch (toStage) {
            case SEED -> 0;
            case SPROUT -> 30;
            case FLOWER -> 50;
            case FRUIT -> 100;
        };
    }

    public static Optional<BadgeCode> rewardBadge(GrowthStage toStage) {
        return switch (toStage) {
            case SEED -> Optional.empty();
            case SPROUT -> Optional.of(BadgeCode.FIRST_SPROUT);
            case FLOWER -> Optional.of(BadgeCode.FIRST_FLOWER);
            case FRUIT -> Optional.of(BadgeCode.FIRST_FRUIT);
        };
    }
}
