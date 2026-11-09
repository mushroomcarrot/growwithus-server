package community.likelion.growwithusserver.common.security.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class TokenInvalidException extends GlobalException {
    public TokenInvalidException() {
        super(ErrorCode.TOKEN_INVALID);
    }
}
