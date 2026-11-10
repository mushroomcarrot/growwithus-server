package community.likelion.growwithusserver.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderFilterTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 서비스_키가_맞으면_SYSTEM_권한을_준다() throws Exception {
        MockHttpServletResponse response = run(new InternalServiceKeyFilter("secret", objectMapper),
                "/internal/weather/sync", InternalServiceKeyFilter.HEADER, "secret");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(currentAuthority()).isEqualTo("ROLE_SYSTEM");
    }

    @Test
    void 서비스_키가_없거나_틀리면_C4014() throws Exception {
        InternalServiceKeyFilter filter = new InternalServiceKeyFilter("secret", objectMapper);

        for (String key : new String[]{null, "", "wrong"}) {
            MockHttpServletResponse response = run(filter, "/internal/weather/sync", InternalServiceKeyFilter.HEADER, key);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("C4014");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        // 서버에 키가 설정되지 않았으면 빈 값으로도 통과할 수 없다
        MockHttpServletResponse response = run(new InternalServiceKeyFilter("", objectMapper),
                "/internal/weather/sync", InternalServiceKeyFilter.HEADER, "");
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 시연_헤더가_있으면_DEMO_권한을_준다() throws Exception {
        MockHttpServletResponse response = run(new DemoModeFilter(true, objectMapper), "/demo/state", DemoModeFilter.HEADER, "true");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(currentAuthority()).isEqualTo("ROLE_DEMO");
    }

    @Test
    void 시연_헤더가_없거나_비활성화면_C4032() throws Exception {
        MockHttpServletResponse noHeader = run(new DemoModeFilter(true, objectMapper), "/demo/state", DemoModeFilter.HEADER, null);
        assertThat(noHeader.getStatus()).isEqualTo(403);
        assertThat(noHeader.getContentAsString()).contains("C4032");

        MockHttpServletResponse disabled = run(new DemoModeFilter(false, objectMapper), "/demo/state", DemoModeFilter.HEADER, "true");
        assertThat(disabled.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 다른_경로는_건드리지_않는다() throws Exception {
        MockHttpServletResponse response = run(new InternalServiceKeyFilter("secret", objectMapper), "/users/me", "X-Other", "x");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        response = run(new DemoModeFilter(false, objectMapper), "/internal/weather/sync", "X-Other", "x");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse run(jakarta.servlet.Filter filter, String path, String header, String value) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        if (value != null) {
            request.addHeader(header, value);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private String currentAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().iterator().next().getAuthority();
    }
}
