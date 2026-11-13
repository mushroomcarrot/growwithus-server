package community.likelion.growwithusserver.game.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 답을 낸 퀴즈일 때
public class QuizAlreadyAnsweredException extends GlobalException {
    public QuizAlreadyAnsweredException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
