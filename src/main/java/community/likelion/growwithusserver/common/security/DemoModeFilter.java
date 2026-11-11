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
import java.util.List;

// /demo/** 요청의 X-Demo-Mode: true 헤더를 검증하고 DEMO 권한을 준다. demo.enabled=false면 모두 거부한다.
@Slf4j
@RequiredArgsConstructor
public class DemoModeFilter extends OncePerRequestFilter {
    public static final String PATH_PREFIX = "/demo/";
    public static final String HEADER = "X-Demo-Mode";
    public static final String PRINCIPAL = "demo";
    public static final String AUTHORITY = "ROLE_DEMO";

    private final boolean enabled;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws IOException, ServletException {
        if (!enabled || !"true".equalsIgnoreCase(request.getHeader(HEADER))) {
            log.warn("[doFilterInternal] 시연 API 접근 거부: {} (활성화: {})", request.getServletPath(), enabled);
            SecurityErrorWriter.write(response, objectMapper, ErrorCode.DEMO_MODE_REQUIRED);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                PRINCIPAL, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        chain.doFilter(request, response);
    }
}
