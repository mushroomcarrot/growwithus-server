package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 취소한 예약을 다시 취소할 때
public class VisitAlreadyCanceledException extends GlobalException {
    public VisitAlreadyCanceledException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
