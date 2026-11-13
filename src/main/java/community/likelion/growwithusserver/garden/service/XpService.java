package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.XpHistory;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

// XP 지급은 여기 한 곳에서만 한다. 텃밭의 누적 XP를 올리고 지급 기록을 남긴다.
@Service
@RequiredArgsConstructor
@Slf4j
public class XpService {
    private final XpHistoryRepository xpHistoryRepository;

    /**
     * 호출한 서비스 메서드의 트랜잭션에 참여한다. 누적 XP를 읽고 고치므로 텃밭은 잠근 상태여야 한다
     * (GardenService.getGardenForUpdate).
     *
     * @return 지급한 뒤의 누적 XP
     */
    public int grant(Garden garden, XpReason reason, int amount) {
        garden.setTotalXp(garden.getTotalXp() + amount);
        xpHistoryRepository.save(XpHistory.builder()
                .garden(garden)
                .reason(reason)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("[grant] XP 지급: 텃밭 {} ({} +{})", garden.getId(), reason, amount);

        return garden.getTotalXp();
    }

    // 오늘 얻은 XP
    public int getTodayXp(Long gardenId) {
        LocalDate today = LocalDate.now();

        return (int) xpHistoryRepository.sumAmount(gardenId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }
}
