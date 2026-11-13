package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 이미 주인 프로필을 등록한 사용자일 때
public class ProfileAlreadyExistsException extends GlobalException {
    public ProfileAlreadyExistsException() {
        super(ErrorCode.PROFILE_ALREADY_EXISTS);
    }
}
