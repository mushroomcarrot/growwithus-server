package community.likelion.growwithusserver.evolution.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 진화 이벤트가 없을 때
public class EvolutionEventNotFoundException extends GlobalException {
    public EvolutionEventNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
