package community.likelion.growwithusserver.game.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 퀴즈에 없는 보기를 골랐을 때
public class QuizChoiceInvalidException extends GlobalException {
    public QuizChoiceInvalidException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
