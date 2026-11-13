package community.likelion.growwithusserver.farm.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 다른 주인에게 분양된 칸일 때
public class PlotAlreadyAssignedException extends GlobalException {
    public PlotAlreadyAssignedException() {
        super(ErrorCode.PLOT_ALREADY_ASSIGNED);
    }
}
