package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.game.data.entity.Badge;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.data.repository.BadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// BadgeCode의 배지 중 badge 테이블에 없는 것을 부팅할 때 넣는다.
@Slf4j
@Component
@Order(BadgeSeeder.ORDER)
@RequiredArgsConstructor
public class BadgeSeeder implements ApplicationRunner {
    // 시연 텃밭 시드(GardenDemoSeeder)가 배지를 쓰므로 그보다 먼저 돈다
    public static final int ORDER = 5;

    private final BadgeRepository badgeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (BadgeCode code : BadgeCode.values()) {
            if (!badgeRepository.existsByCode(code.name())) {
                badgeRepository.save(Badge.builder()
                        .code(code.name())
                        .name(code.getDisplayName())
                        .description(code.getDescription())
                        .build());
                log.info("[run] 배지 시드 데이터 적재: {}", code);
            }
        }
    }
}
