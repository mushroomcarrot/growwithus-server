package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.UserQuest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserQuestRepository extends JpaRepository<UserQuest, Long> {
    Optional<UserQuest> findByQuestIdAndUserId(Long questId, Long userId);

    List<UserQuest> findAllByUserIdAndQuestIdIn(Long userId, List<Long> questIds);
}
