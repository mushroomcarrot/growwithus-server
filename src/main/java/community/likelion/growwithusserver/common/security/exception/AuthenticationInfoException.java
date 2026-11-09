package community.likelion.growwithusserver.common.security.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class AuthenticationInfoException extends GlobalException {
    public AuthenticationInfoException() {
        super(ErrorCode.VERIFICATION_INVALID);
    }
}
