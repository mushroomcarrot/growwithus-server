package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.Quest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface QuestRepository extends JpaRepository<Quest, Long> {
    List<Quest> findAllByWeekStartOrderByIdAsc(LocalDate weekStart);
}
