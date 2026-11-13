package community.likelion.growwithusserver.evolution.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 확인한 진화 이벤트일 때
public class EvolutionEventAlreadyAckedException extends GlobalException {
    public EvolutionEventAlreadyAckedException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
