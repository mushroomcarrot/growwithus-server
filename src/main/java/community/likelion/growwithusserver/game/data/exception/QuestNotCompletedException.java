package community.likelion.growwithusserver.game.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 아직 달성하지 않은 퀘스트의 보상을 받으려 할 때
public class QuestNotCompletedException extends GlobalException {
    public QuestNotCompletedException() {
        super(ErrorCode.NOT_COMPLETED);
    }
}
