package community.likelion.growwithusserver.notification.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 알림이 없거나 내 알림이 아닐 때
public class NotificationNotFoundException extends GlobalException {
    public NotificationNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
