package community.likelion.growwithusserver.game.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 퀘스트가 없을 때
public class QuestNotFoundException extends GlobalException {
    public QuestNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
