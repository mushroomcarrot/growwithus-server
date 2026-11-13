package community.likelion.growwithusserver.farm.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class PlotRequestInvalidException extends GlobalException {
    public PlotRequestInvalidException() {
        super(ErrorCode.INVALID_PARAMETER);
    }
}
