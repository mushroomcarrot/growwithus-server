package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.garden.care.GardenCareLog;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.dto.HeartResponseDto;
import community.likelion.growwithusserver.garden.data.entity.CareLogHeart;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.data.exception.CareLogNotFoundException;
import community.likelion.growwithusserver.garden.data.exception.HeartAlreadySentException;
import community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareLogHeartService {
    public static final int HEART_XP = 5;

    private final GardenCareLogReader gardenCareLogReader;
    private final GardenService gardenService;
    private final XpService xpService;
    private final CareLogHeartRepository careLogHeartRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 감사 하트. 돌봄 기록 하나에 한 번만 보낼 수 있고, 돌본 관리사에게 알림이 간다
    @Transactional
    public GlobalResponse<HeartResponseDto> sendHeart(String username, String careLogId) {
        GardenCareLog careLog = gardenCareLogReader.findCareLog(IdPrefix.CARE_LOG.parse(careLogId))
                .orElseThrow(() -> {
                    log.error("[sendHeart] 돌봄 기록 조회 실패: {}", careLogId);
                    return new CareLogNotFoundException();
                });

        // 텃밭을 잠가 같은 기록에 하트가 동시에 두 번 들어오지 않게 한다
        Garden garden = gardenService.getGardenForUpdate(careLog.gardenId());
        gardenService.requireOwner(username, garden);
        if (careLogHeartRepository.existsByCareLogId(careLog.careLogId())) {
            log.error("[sendHeart] 이미 하트를 보낸 돌봄 기록: {}", careLogId);
            throw new HeartAlreadySentException();
        }

        careLogHeartRepository.save(CareLogHeart.builder()
                .careLogId(careLog.careLogId())
                .gardenId(garden.getId())
                .user(garden.getOwner())
                .caretakerUsername(careLog.caretakerUsername())
                .createdAt(LocalDateTime.now())
                .build());
        xpService.grant(garden, XpReason.HEART, HEART_XP);
        notifyCaretaker(garden, careLog, careLogId);

        return GlobalResponse.ok(HeartResponseDto.builder()
                .careLogId(careLogId)
                .hearted(true)
                .xpGained(HEART_XP)
                .build());
    }

    private void notifyCaretaker(Garden garden, GardenCareLog careLog, String careLogId) {
        User caretaker = userRepository.findByUsername(careLog.caretakerUsername()).orElse(null);
        if (caretaker == null) {
            log.warn("[notifyCaretaker] 관리사 계정이 없어 하트 알림을 보내지 않음: {}", careLogId);
            return;
        }

        String nickname = ownerProfileRepository.findByUserId(garden.getOwner().getId())
                .map(OwnerProfile::getNickname)
                .orElse(garden.getOwner().getName());
        notificationService.send(NotificationSendDto.builder()
                .receiver(caretaker)
                .type(NotificationType.HEART)
                .title(nickname + " 가족이 고맙다고 하트를 보냈어요")
                .body(careLog.summary())
                .targetScreen("DT_CARE_DONE")
                .targetId(careLogId)
                .build());
    }
}
