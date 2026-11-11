package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 슬롯 정원이 찼을 때
public class VisitSlotFullException extends GlobalException {
    public VisitSlotFullException() {
        super(ErrorCode.VISIT_SLOT_FULL);
    }
}
