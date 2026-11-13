package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 돌봄 기록이 없을 때
public class CareLogNotFoundException extends GlobalException {
    public CareLogNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
