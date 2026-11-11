package community.likelion.growwithusserver.demo.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 시연에서 쓸 수 없는 날씨 (SUNNY, RAIN, HOT 외)
public class DemoWeatherNotAllowedException extends GlobalException {
    public DemoWeatherNotAllowedException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
