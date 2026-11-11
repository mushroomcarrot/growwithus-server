package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 내 텃밭이 아닐 때
public class GardenAccessDeniedException extends GlobalException {
    public GardenAccessDeniedException() {
        super(ErrorCode.ACCESS_DENIED);
    }
}
