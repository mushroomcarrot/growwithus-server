package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 같은 날 예약이 이미 있을 때
public class VisitAlreadyReservedException extends GlobalException {
    public VisitAlreadyReservedException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
