package community.likelion.growwithusserver.caretaker;

import community.likelion.growwithusserver.common.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VisionClientTest {
    @Test void distinguishesNegativeFindingFromUnavailableInference() {
        var mapper = JsonMapper.builder().build();
        for (String answer : new String[]{"{\"matched\":true,\"message\":\"잎이 보여요.\"}",
                "{\"matched\":false,\"message\":\"사진을 다시 확인해주세요.\"}"}) {
            var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
            var properties = new LocalLlmProperties(); properties.setVisionModel("qwen2.5vl:3b");
            server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                    .andRespond(withSuccess(mapper.writeValueAsString(java.util.Map.of("message", java.util.Map.of("content", answer))), MediaType.APPLICATION_JSON));
            var result = new OllamaVisionClient(builder.build(), properties, mapper).check(new byte[]{1}, "잎 확인");
            assertTrue(result.isPresent()); assertEquals(mapper.readTree(answer).path("matched").asBoolean(), result.get().matched());
            server.verify();
        }
    }
    @Test void invalidModelOutputAndBusyWorkerReturnUnknown() {
        var mapper = JsonMapper.builder().build();
        var properties = new LocalLlmProperties(); properties.setVisionModel("qwen2.5vl:3b");
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:11434/api/chat")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:11434/api/chat")).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        var client = new OllamaVisionClient(builder.build(), properties, mapper);
        assertTrue(client.check(new byte[]{1}, "check").isEmpty()); assertTrue(client.check(new byte[]{1}, "check").isEmpty());
        assertTrue(client.check(null, "check").isEmpty()); server.verify();
    }
}
