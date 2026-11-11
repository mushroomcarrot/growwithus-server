package community.likelion.growwithusserver.user.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    // 가입 직후, 역할 선택 전
    USER("ROLE_USER"),
    OWNER("ROLE_OWNER"),
    CARETAKER("ROLE_CARETAKER"),
    ADMIN("ROLE_ADMIN");

    private final String value;
}
