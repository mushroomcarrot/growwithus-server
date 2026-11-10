package community.likelion.growwithusserver.user.data.enums;

// 로그인 후 클라이언트가 이동할 화면. 계산은 UserService.resolveNextStep 한 곳에서만 한다.
public enum NextStep {
    ROLE_SELECT,
    PROFILE_OWNER,
    PROFILE_CARE,
    OWNER_HOME,
    CARE_TODAY
}
