package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 방문 하루 전이 지나 취소할 수 없을 때
public class VisitCancelDeadlinePassedException extends GlobalException {
    public VisitCancelDeadlinePassedException() {
        super(ErrorCode.CANCEL_DEADLINE_PASSED);
    }
}
