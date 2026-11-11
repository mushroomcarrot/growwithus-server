package community.likelion.growwithusserver.harvest.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 텃밭이 없을 때
public class GardenNotFoundException extends GlobalException {
    public GardenNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
