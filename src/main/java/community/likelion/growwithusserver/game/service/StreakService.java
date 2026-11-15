package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.game.data.dto.StreakRemindResponseDto;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.notification.data.dto.NotificationSendDto;
import community.likelion.growwithusserver.notification.data.enums.NotificationType;
import community.likelion.growwithusserver.notification.service.NotificationService;
import community.likelion.growwithusserver.game.data.dto.StreakCheckResponseDto;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 스트릭 배치. 끊김 처리 (매일 00:05)는 전날 응원하지 않은 텃밭의 스트릭을 0으로 돌리고 캐릭터를 시무룩하게 한다.
// 위험 알림 (매일 20:00)은 오늘 아직 응원하지 않은 주인에게 하루 한 번 알린다.
@Service
@RequiredArgsConstructor
@Slf4j
public class StreakService {
    private final GardenRepository gardenRepository;
    private final NotificationService notificationService;

    /**
     * @param date 응원했는지 확인할 날. null이면 어제. 그날이나 그 뒤에 응원한 텃밭은 건드리지 않는다
     */
    @Transactional
    public StreakCheckResponseDto check(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now().minusDays(1) : date;
        int broken = gardenRepository.breakStreaks(target, CharacterMood.SAD);
        log.info("[check] 스트릭 끊김 처리: {} (텃밭 {}개)", target, broken);

        return new StreakCheckResponseDto(broken);
    }

    // 스트릭이 이어지는 중인데 오늘 아직 응원하지 않은 주인에게 STREAK_RISK 알림을 보낸다. 같은 날 다시 실행해도 한 번만 보낸다.
    @Transactional
    public StreakRemindResponseDto remind() {
        LocalDate today = LocalDate.now();
        int reminded = 0;
        for (Garden garden : gardenRepository.findStreaksAtRisk(today)) {
            if (notificationService.hasSentSince(garden.getOwner(), NotificationType.STREAK_RISK, today.atStartOfDay())) {
                continue;
            }
            notificationService.send(NotificationSendDto.builder()
                    .receiver(garden.getOwner())
                    .type(NotificationType.STREAK_RISK)
                    .title("오늘 응원을 아직 안 했어요")
                    .body(garden.getStreakDays() + "일째 이어 온 응원이 오늘 밤에 끊겨요. " + garden.getCharacterName() + "를 응원해 주세요.")
                    .targetScreen("DT_HOME")
                    .targetId(IdPrefix.GARDEN.format(garden.getId()))
                    .build());
            reminded++;
        }
        log.info("[remind] 스트릭 위험 알림: {} (주인 {}명)", today, reminded);

        return new StreakRemindResponseDto(reminded);
    }
}
