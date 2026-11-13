package community.likelion.growwithusserver.game.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 퀴즈가 없을 때
public class QuizNotFoundException extends GlobalException {
    public QuizNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
