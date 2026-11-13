package community.likelion.growwithusserver.game.data.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerRequestDto {
    // 고른 보기의 순번 (0부터)
    @NotNull
    private Integer choiceIndex;
}
