package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 지난 날짜나 오늘처럼 예약을 받지 않는 슬롯일 때, 조회 기간이 잘못됐을 때
public class VisitSlotNotBookableException extends GlobalException {
    public VisitSlotNotBookableException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
