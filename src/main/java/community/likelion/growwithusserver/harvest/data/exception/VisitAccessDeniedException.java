package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 내 방문 예약이 아닐 때
public class VisitAccessDeniedException extends GlobalException {
    public VisitAccessDeniedException() {
        super(ErrorCode.ACCESS_DENIED);
    }
}
