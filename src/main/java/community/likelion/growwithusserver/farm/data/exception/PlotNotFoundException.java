package community.likelion.growwithusserver.farm.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class PlotNotFoundException extends GlobalException {
    public PlotNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
