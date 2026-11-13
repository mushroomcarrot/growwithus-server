package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.game.data.entity.Badge;
import community.likelion.growwithusserver.game.data.entity.UserBadge;
import community.likelion.growwithusserver.game.data.enums.BadgeCode;
import community.likelion.growwithusserver.game.data.repository.BadgeRepository;
import community.likelion.growwithusserver.game.data.repository.UserBadgeRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

// 배지 지급은 여기 한 곳에서만 한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;

    /**
     * 배지를 준다. 이미 가진 배지면 다시 주지 않는다. 호출한 서비스 메서드의 트랜잭션에 참여한다.
     *
     * @return 그 배지. 등록되지 않은 배지 코드면 비어 있다
     */
    public Optional<Badge> grant(User user, BadgeCode code) {
        Optional<Badge> badge = badgeRepository.findByCode(code.name());
        if (badge.isEmpty()) {
            log.warn("[grant] 등록되지 않은 배지: {}", code);
            return badge;
        }

        if (!userBadgeRepository.existsByUserIdAndBadgeId(user.getId(), badge.get().getId())) {
            userBadgeRepository.save(UserBadge.builder()
                    .user(user)
                    .badge(badge.get())
                    .acquiredAt(LocalDateTime.now())
                    .build());
            log.info("[grant] 배지 지급: {} ({})", user.getUsername(), code);
        }

        return badge;
    }
}
