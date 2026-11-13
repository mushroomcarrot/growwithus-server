package community.likelion.growwithusserver.game.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 보상을 받은 퀘스트일 때
public class QuestAlreadyClaimedException extends GlobalException {
    public QuestAlreadyClaimedException() {
        super(ErrorCode.ALREADY_PROCESSED);
    }
}
