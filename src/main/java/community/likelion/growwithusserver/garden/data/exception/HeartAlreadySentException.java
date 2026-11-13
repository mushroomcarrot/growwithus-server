package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 하트를 보낸 돌봄 기록일 때
public class HeartAlreadySentException extends GlobalException {
    public HeartAlreadySentException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
