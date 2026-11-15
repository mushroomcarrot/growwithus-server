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
}
