package community.likelion.growwithusserver.weather.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 기상청 API 호출 실패 (통신 오류, 읽을 수 없는 응답, resultCode가 정상이 아님)
public class KmaApiException extends GlobalException {
    public KmaApiException() {
        super(ErrorCode.EXTERNAL_API_ERROR);
    }
}
