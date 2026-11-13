package community.likelion.growwithusserver.game.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.game.data.dto.QuizAnswerRequestDto;
import community.likelion.growwithusserver.game.data.dto.QuizAnswerResponseDto;
import community.likelion.growwithusserver.game.data.dto.QuizResponseDto;
import community.likelion.growwithusserver.game.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;

    @GetMapping("/gardens/{gardenId}/quizzes/today")
    @Operation(summary = "[확장] 오늘의 텃밭 퀴즈")
    public GlobalResponse<QuizResponseDto> getToday(@PathVariable String gardenId) {
        return quizService.getToday(SecurityUtil.getUsername(), gardenId);
    }

    @PostMapping("/quizzes/{quizId}/answers")
    @Operation(summary = "[확장] 퀴즈 답 제출 (한 번만)")
    public GlobalResponse<QuizAnswerResponseDto> answer(@PathVariable String quizId,
                                                        @Valid @RequestBody QuizAnswerRequestDto quizAnswerRequestDto) {
        return quizService.answer(SecurityUtil.getUsername(), quizId, quizAnswerRequestDto.getChoiceIndex());
    }
}
