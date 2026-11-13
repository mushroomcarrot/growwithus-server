package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 주인의 거주 구로 고를 수 없는 값일 때 (SUJI, GIHEUNG만 가능)
public class OwnerDistrictNotAllowedException extends GlobalException {
    public OwnerDistrictNotAllowedException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
