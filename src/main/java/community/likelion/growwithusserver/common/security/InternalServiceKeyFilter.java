package community.likelion.growwithusserver.common.security;

import community.likelion.growwithusserver.common.response.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// /internal/** 요청의 X-Service-Key 헤더를 검증하고 SYSTEM 권한을 준다
@Slf4j
@RequiredArgsConstructor
public class InternalServiceKeyFilter extends OncePerRequestFilter {
    public static final String PATH_PREFIX = "/internal/";
    public static final String HEADER = "X-Service-Key";
    public static final String PRINCIPAL = "system";
    public static final String AUTHORITY = "ROLE_SYSTEM";

    private final String serviceKey;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws IOException, ServletException {
        if (!isValid(request.getHeader(HEADER))) {
            log.warn("[doFilterInternal] 서비스 키 불일치로 내부 API 접근 거부: {}", request.getServletPath());
            SecurityErrorWriter.write(response, objectMapper, ErrorCode.SERVICE_KEY_INVALID);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                PRINCIPAL, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        chain.doFilter(request, response);
    }

    // 서버에 키가 설정되지 않았으면 항상 거부한다
    private boolean isValid(String requestKey) {
        if (serviceKey == null || serviceKey.isBlank() || requestKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                serviceKey.getBytes(StandardCharsets.UTF_8),
                requestKey.getBytes(StandardCharsets.UTF_8));
    }
}
