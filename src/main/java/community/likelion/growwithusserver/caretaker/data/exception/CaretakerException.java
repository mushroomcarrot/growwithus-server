package community.likelion.growwithusserver.caretaker.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class CaretakerException extends GlobalException {
    public CaretakerException(ErrorCode errorCode) {
        super(errorCode);
    }
}
