package community.likelion.growwithusserver.common.security;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

// 필터 단계(컨트롤러 밖)에서 GlobalResponse 형식의 에러 응답을 쓴다
public final class SecurityErrorWriter {
    private SecurityErrorWriter() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(errorCode.getHttpStatus());
        response.getWriter().write(objectMapper.writeValueAsString(GlobalResponse.error(errorCode)));
    }
}
