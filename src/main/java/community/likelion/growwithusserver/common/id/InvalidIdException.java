package community.likelion.growwithusserver.common.id;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class InvalidIdException extends GlobalException {
    public InvalidIdException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
