package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BadgeRepository extends JpaRepository<Badge, Long> {
    Optional<Badge> findByCode(String code);

    boolean existsByCode(String code);
}
