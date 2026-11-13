package community.likelion.growwithusserver.diary.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 일기의 cursor가 날짜(YYYY-MM-DD) 형식이 아닐 때
public class DiaryCursorInvalidException extends GlobalException {
    public DiaryCursorInvalidException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
