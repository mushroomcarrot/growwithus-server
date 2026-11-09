package community.likelion.growwithusserver.user.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class PasswordInvalidException extends GlobalException {
    public PasswordInvalidException() {
        super(ErrorCode.PASSWORD_INVALID);
    }
}