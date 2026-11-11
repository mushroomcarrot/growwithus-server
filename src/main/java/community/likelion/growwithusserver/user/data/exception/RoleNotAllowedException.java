package community.likelion.growwithusserver.user.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class RoleNotAllowedException extends GlobalException {
    public RoleNotAllowedException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
