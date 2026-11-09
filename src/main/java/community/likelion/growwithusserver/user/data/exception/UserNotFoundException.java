package community.likelion.growwithusserver.user.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class UserNotFoundException extends GlobalException {
    public UserNotFoundException() {
        super(ErrorCode.USER_DATA_NOT_FOUND);
    }
}