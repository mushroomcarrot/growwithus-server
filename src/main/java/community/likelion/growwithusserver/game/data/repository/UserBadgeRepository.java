package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);

    // 얻은 순서대로
    @Query("select ub from UserBadge ub join fetch ub.badge where ub.user.username = :username order by ub.id asc")
    List<UserBadge> findAllByUsername(String username);
}
