package community.likelion.growwithusserver.garden.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

// 역할을 고르기 전(USER)에 주인 프로필을 등록하려 할 때
public class OwnerRoleRequiredException extends GlobalException {
    public OwnerRoleRequiredException() {
        super(ErrorCode.ROLE_NOT_SELECTED);
    }
}
