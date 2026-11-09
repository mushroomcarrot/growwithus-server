package community.likelion.growwithusserver.user.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class EmailDuplicationException extends GlobalException {
    public EmailDuplicationException() {
        super(ErrorCode.EMAIL_DUPLICATION);
    }
}
