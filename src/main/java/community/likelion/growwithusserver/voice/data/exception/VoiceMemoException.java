package community.likelion.growwithusserver.voice.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

/** 음성 메모와 담당 작업 검증에 쓰는 공통 오류다. */
public class VoiceMemoException extends GlobalException {
    public VoiceMemoException(ErrorCode errorCode) {
        super(errorCode);
    }
}
