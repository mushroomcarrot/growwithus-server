package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.game.data.dto.QuizAnswerResponseDto;
import community.likelion.growwithusserver.game.data.dto.QuizResponseDto;
import community.likelion.growwithusserver.game.data.entity.GardenQuiz;
import community.likelion.growwithusserver.game.data.exception.QuizAlreadyAnsweredException;
import community.likelion.growwithusserver.game.data.exception.QuizChoiceInvalidException;
import community.likelion.growwithusserver.game.data.exception.QuizNotFoundException;
import community.likelion.growwithusserver.game.data.repository.GardenQuizRepository;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.XpReason;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

// [확장] 오늘의 텃밭 퀴즈. 텃밭마다 하루에 한 문제가 나가고 맞히면 XP를 준다.
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {
    public static final int QUIZ_XP = 10;

    private final GardenQuizRepository gardenQuizRepository;
    private final GardenService gardenService;
    private final XpService xpService;

    // 오늘의 퀴즈. 그날 처음 조회할 때 문제를 정해 저장한다. 텃밭을 잠가 같은 날 문제가 두 번 생기지 않게 한다
    @Transactional
    public GlobalResponse<QuizResponseDto> getToday(String username, String gardenId) {
        Garden garden = gardenService.getOwnedGardenForUpdate(username, gardenId);
        LocalDate today = LocalDate.now();

        GardenQuiz gardenQuiz = gardenQuizRepository.findByGardenIdAndQuizDate(garden.getId(), today)
                .orElseGet(() -> gardenQuizRepository.save(GardenQuiz.builder()
                        .garden(garden)
                        .quizDate(today)
                        // 날마다 다음 문제로 넘어가고, 텃밭마다 시작하는 문제가 다르다
                        .bankIndex(Math.floorMod(today.toEpochDay() + garden.getId(), QuizBank.size()))
                        .build()));
        QuizBank.Quiz quiz = QuizBank.get(gardenQuiz.getBankIndex());

        return GlobalResponse.ok(QuizResponseDto.builder()
                .quizId(IdPrefix.QUIZ.format(gardenQuiz.getId()))
                .question(quiz.question())
                .choices(quiz.choices())
                .answered(gardenQuiz.isAnswered())
                .build());
    }

    // 답은 한 번만 낼 수 있다. 맞히면 XP를 준다
    @Transactional
    public GlobalResponse<QuizAnswerResponseDto> answer(String username, String quizId, int choiceIndex) {
        Long id = IdPrefix.QUIZ.parse(quizId);
        // 내 텃밭을 먼저 잠가 같은 퀴즈에 답이 동시에 두 번 들어오지 않게 한다
        Garden garden = gardenService.findMyGardenForUpdate(username).orElse(null);
        GardenQuiz gardenQuiz = gardenQuizRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("[answer] 퀴즈 조회 실패: {}", quizId);
                    return new QuizNotFoundException();
                });
        if (garden == null || !garden.getId().equals(gardenQuiz.getGarden().getId())) {
            log.error("[answer] 내 텃밭의 퀴즈가 아님: {} ({})", username, quizId);
            throw new GardenAccessDeniedException();
        }

        QuizBank.Quiz quiz = QuizBank.get(gardenQuiz.getBankIndex());
        if (gardenQuiz.isAnswered()) {
            log.error("[answer] 이미 답을 낸 퀴즈: {}", quizId);
            throw new QuizAlreadyAnsweredException();
        }
        if (choiceIndex < 0 || choiceIndex >= quiz.choices().size()) {
            log.error("[answer] 없는 보기: {} ({})", quizId, choiceIndex);
            throw new QuizChoiceInvalidException();
        }

        boolean correct = choiceIndex == quiz.answerIndex();
        gardenQuiz.setChoiceIndex(choiceIndex);
        gardenQuiz.setCorrect(correct);
        gardenQuiz.setAnsweredAt(LocalDateTime.now());
        if (correct) {
            xpService.grant(garden, XpReason.QUIZ, QUIZ_XP);
        }

        return GlobalResponse.ok(QuizAnswerResponseDto.builder()
                .correct(correct)
                .explanation(quiz.explanation())
                .xpGained(correct ? QUIZ_XP : 0)
                .build());
    }
}
