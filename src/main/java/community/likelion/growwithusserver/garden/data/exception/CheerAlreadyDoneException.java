package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 오늘 이미 응원했을 때
public class CheerAlreadyDoneException extends GlobalException {
    public CheerAlreadyDoneException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
