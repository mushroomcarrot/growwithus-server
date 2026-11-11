package community.likelion.growwithusserver.common.ai;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// LLM, STT, TTS 호출 실패. 규칙 기반 대체 로직이 있는 곳은 이 예외를 잡아서 처리한다.
public class AiClientException extends GlobalException {
    public AiClientException() {
        super(ErrorCode.EXTERNAL_API_ERROR);
    }
}
