package community.likelion.growwithusserver.common.security;

import community.likelion.growwithusserver.common.security.exception.AuthenticationInfoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {
    // 현재 사용자의 로그인 아이디 (JWT subject)
    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AuthenticationInfoException();
        }
        return authentication.getName();
    }
}
