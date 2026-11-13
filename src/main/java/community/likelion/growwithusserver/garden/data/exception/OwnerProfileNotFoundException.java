package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 주인 프로필이 없을 때
public class OwnerProfileNotFoundException extends GlobalException {
    public OwnerProfileNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
