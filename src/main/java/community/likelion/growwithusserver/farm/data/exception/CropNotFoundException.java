package community.likelion.growwithusserver.farm.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class CropNotFoundException extends GlobalException {
    public CropNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
