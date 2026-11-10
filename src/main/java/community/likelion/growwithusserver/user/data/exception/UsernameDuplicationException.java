package community.likelion.growwithusserver.user.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class UsernameDuplicationException extends GlobalException {
    public UsernameDuplicationException() {
        super(ErrorCode.USERNAME_DUPLICATION);
    }
}
