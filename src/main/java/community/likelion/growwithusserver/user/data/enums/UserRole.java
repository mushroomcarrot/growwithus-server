package community.likelion.growwithusserver.user.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    ADMIN("ROLE_ADMIN"),
    OWNER("ROLE_OWNER"),
    MANAGER("ROLE_MANAGER");

    private final String value;
}
