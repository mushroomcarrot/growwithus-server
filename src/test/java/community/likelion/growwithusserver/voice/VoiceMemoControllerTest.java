package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.common.response.GlobalExceptionHandler;
import community.likelion.growwithusserver.voice.controller.VoiceMemoController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 컨트롤러와 공통 오류 매핑 검사. 실제 JWT 필터 체인 검사는 별도다. */
class VoiceMemoControllerTest {
    @Test
    void 업로드와_확정_및_요청_오류() throws Exception {
        VoiceMemoSelfTest.Fixture fixture = new VoiceMemoSelfTest.Fixture();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VoiceMemoController(fixture.service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "caretaker", "unused", List.of(new SimpleGrantedAuthority("ROLE_CARETAKER"))));
        try {
            var uploaded = mvc.perform(multipart("/caretaker/tasks/tsk_1/voice-memos")
                    .file(VoiceMemoSelfTest.audio())).andReturn().getResponse();
            assertEquals(200, uploaded.getStatus());
            assertTrue(uploaded.getContentAsString().contains("vm_1"));
            var recovered = mvc.perform(get("/caretaker/tasks/tsk_1/voice-memos/latest")).andReturn().getResponse();
            assertEquals(200, recovered.getStatus());
            assertTrue(recovered.getContentAsString().contains("vm_1"));
            assertTrue(recovered.getContentAsString().contains("READY"));
            var missing = mvc.perform(multipart("/caretaker/tasks/tsk_1/voice-memos")).andReturn().getResponse();
            assertEquals(400, missing.getStatus());
            assertTrue(missing.getContentAsString().contains("C002"));
            var confirmed = mvc.perform(post("/caretaker/voice-memos/vm_1/confirm")).andReturn().getResponse();
            assertEquals(200, confirmed.getStatus());
            assertTrue(confirmed.getContentAsString().contains("CONFIRMED"));
            var invalid = mvc.perform(post("/caretaker/voice-memos/vm_1/confirm")
                    .contentType(MediaType.APPLICATION_JSON).content("{not-json")).andReturn().getResponse();
            assertEquals(400, invalid.getStatus());
            assertTrue(invalid.getContentAsString().contains("C002"));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(401, mvc.perform(post("/caretaker/voice-memos/vm_1/confirm")).andReturn().getResponse().getStatus());
    }
}
