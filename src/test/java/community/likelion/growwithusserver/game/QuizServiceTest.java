package community.likelion.growwithusserver.game;

import community.likelion.growwithusserver.game.data.dto.QuizAnswerResponseDto;
import community.likelion.growwithusserver.game.data.dto.QuizResponseDto;
import community.likelion.growwithusserver.game.data.entity.GardenQuiz;
import community.likelion.growwithusserver.game.data.exception.QuizAlreadyAnsweredException;
import community.likelion.growwithusserver.game.data.exception.QuizChoiceInvalidException;
import community.likelion.growwithusserver.game.data.exception.QuizNotFoundException;
import community.likelion.growwithusserver.game.data.repository.GardenQuizRepository;
import community.likelion.growwithusserver.game.service.QuizBank;
import community.likelion.growwithusserver.game.service.QuizService;
import community.likelion.growwithusserver.garden.GardenFixtures;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.XpHistoryRepository;
import community.likelion.growwithusserver.garden.service.GardenService;
import community.likelion.growwithusserver.garden.service.XpService;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private GardenQuizRepository gardenQuizRepository;
    @Mock
    private GardenRepository gardenRepository;
    @Mock
    private XpHistoryRepository xpHistoryRepository;

    private QuizService quizService;
    private Garden garden;
    private GardenQuiz gardenQuiz;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(gardenQuizRepository, new GardenService(gardenRepository), new XpService(xpHistoryRepository));
        garden = GardenFixtures.garden();
        // 문제 은행의 첫 문제 (정답은 0번)
        gardenQuiz = GardenQuiz.builder().id(31L).garden(garden).quizDate(LocalDate.now()).bankIndex(0).build();

        lenient().when(gardenRepository.findIdByOwnerUsername("minji")).thenReturn(Optional.of(3L));
        lenient().when(gardenRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(garden));
        lenient().when(gardenQuizRepository.findById(31L)).thenReturn(Optional.of(gardenQuiz));
    }

    @Test
    void 오늘의_퀴즈는_처음_조회할_때_정해지고_다시_조회하면_같은_문제다() {
        when(gardenQuizRepository.findByGardenIdAndQuizDate(3L, LocalDate.now())).thenReturn(Optional.empty());
        when(gardenQuizRepository.save(any(GardenQuiz.class))).thenAnswer(invocation -> {
            GardenQuiz saved = invocation.getArgument(0);
            saved.setId(31L);
            return saved;
        });

        QuizResponseDto first = quizService.getToday("minji", "gdn_3").getData();

        assertThat(first.getQuizId()).isEqualTo("qz_31");
        assertThat(first.getQuestion()).isNotBlank();
        assertThat(first.getChoices()).hasSize(3);
        assertThat(first.isAnswered()).isFalse();

        when(gardenQuizRepository.findByGardenIdAndQuizDate(3L, LocalDate.now())).thenReturn(Optional.of(gardenQuiz));
        QuizResponseDto again = quizService.getToday("minji", "gdn_3").getData();

        assertThat(again.getQuizId()).isEqualTo("qz_31");
        verify(gardenQuizRepository).save(any(GardenQuiz.class));
    }

    @Test
    void 정답을_맞히면_XP를_받는다() {
        QuizAnswerResponseDto response = quizService.answer("minji", "qz_31", 0).getData();

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.getXpGained()).isEqualTo(10);
        assertThat(response.getExplanation()).isEqualTo(QuizBank.get(0).explanation());
        assertThat(garden.getTotalXp()).isEqualTo(1250);
        assertThat(gardenQuiz.isAnswered()).isTrue();
        assertThat(gardenQuiz.getCorrect()).isTrue();
    }

    @Test
    void 틀리면_XP는_없고_풀이는_알려준다() {
        QuizAnswerResponseDto response = quizService.answer("minji", "qz_31", 2).getData();

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.getXpGained()).isZero();
        assertThat(response.getExplanation()).isNotBlank();
        assertThat(garden.getTotalXp()).isEqualTo(1240);
        verify(xpHistoryRepository, never()).save(any());
    }

    @Test
    void 답은_한_번만_낼_수_있고_없는_보기는_고를_수_없다() {
        assertThatThrownBy(() -> quizService.answer("minji", "qz_31", 3)).isInstanceOf(QuizChoiceInvalidException.class);
        assertThatThrownBy(() -> quizService.answer("minji", "qz_31", -1)).isInstanceOf(QuizChoiceInvalidException.class);
        assertThat(gardenQuiz.isAnswered()).isFalse();

        quizService.answer("minji", "qz_31", 1);
        assertThatThrownBy(() -> quizService.answer("minji", "qz_31", 0)).isInstanceOf(QuizAlreadyAnsweredException.class);
        assertThat(garden.getTotalXp()).isEqualTo(1240);
    }

    @Test
    void 남의_퀴즈와_없는_퀴즈에는_답할_수_없다() {
        assertThatThrownBy(() -> quizService.answer("other", "qz_31", 0)).isInstanceOf(GardenAccessDeniedException.class);

        when(gardenQuizRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> quizService.answer("minji", "qz_99", 0)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void 문제_은행의_정답은_보기_안에_있다() {
        for (int i = 0; i < QuizBank.size(); i++) {
            QuizBank.Quiz quiz = QuizBank.get(i);
            assertThat(quiz.answerIndex()).isBetween(0, quiz.choices().size() - 1);
        }
    }
}
