package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.GardenQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface GardenQuizRepository extends JpaRepository<GardenQuiz, Long> {
    Optional<GardenQuiz> findByGardenIdAndQuizDate(Long gardenId, LocalDate quizDate);
}
