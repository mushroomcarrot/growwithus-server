package community.likelion.growwithusserver.common.security;

import community.likelion.growwithusserver.common.response.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

// /demo/** 요청의 X-Demo-Mode: true 헤더를 검증한다. demo.enabled=false면 모두 거부한다.
// 인증은 JWT가 맡고, ADMIN 권한 확인은 SecurityConfiguration이 한다. 헤더만으로는 통과할 수 없다.
@Slf4j
@RequiredArgsConstructor
public class DemoModeFilter extends OncePerRequestFilter {
    public static final String PATH_PREFIX = "/demo/";
    public static final String HEADER = "X-Demo-Mode";

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

        chain.doFilter(request, response);
    }
}
