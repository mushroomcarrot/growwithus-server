package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 방문 예약이 없을 때
public class VisitNotFoundException extends GlobalException {
    public VisitNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
