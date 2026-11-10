package community.likelion.growwithusserver.common.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import community.likelion.growwithusserver.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    private static final String ROLE_NOT_SELECTED_AUTHORITY = "ROLE_USER";

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        // 역할을 아직 고르지 않은 사용자(USER)는 역할 선택 화면으로 보내도록 다른 코드를 준다
        ErrorCode errorCode = isRoleNotSelected() ? ErrorCode.ROLE_NOT_SELECTED : ErrorCode.ACCESS_DENIED;
        SecurityErrorWriter.write(response, objectMapper, errorCode);
    }

    private boolean isRoleNotSelected() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_NOT_SELECTED_AUTHORITY.equals(authority.getAuthority()));
    }
}
