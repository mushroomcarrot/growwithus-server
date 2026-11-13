package community.likelion.growwithusserver.caretaker;

import community.likelion.growwithusserver.common.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LocalTtsClientTest {
    @Test void realJdkHttpClientSendsLengthInsteadOfChunkedJson() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var lengthHeader = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/synthesize", exchange -> {
            try {
                byte[] request = exchange.getRequestBody().readAllBytes();
                String length = exchange.getRequestHeaders().getFirst("Content-Length"); lengthHeader.set(length);
                if (exchange.getRequestHeaders().containsKey("Transfer-Encoding") || length == null
                        || Integer.parseInt(length) != request.length) {
                    exchange.sendResponseHeaders(400, -1); return;
                }
                byte[] audio = {'I','D','3',1};
                exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
                exchange.sendResponseHeaders(200, audio.length); exchange.getResponseBody().write(audio);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            var properties = new LocalTtsProperties(); properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var http = new community.likelion.growwithusserver.common.http.RestClientConfiguration()
                    .externalRestClient(java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(10));
            assertArrayEquals(new byte[]{'I','D','3',1}, new LocalTtsClient(http, properties).synthesize("오늘 잎을 확인해요."));
            assertNotNull(lengthHeader.get());
        } finally { server.stop(0); }
    }
    @Test void sendsSpeedAndReturnsMpegAudio() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8766/synthesize"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.2")))
                .andRespond(withSuccess(new byte[]{'I','D','3',1}, MediaType.valueOf("audio/mpeg")));
        assertArrayEquals(new byte[]{'I','D','3',1}, new LocalTtsClient(builder.build(), new LocalTtsProperties()).synthesize("안녕하세요", 1.2));
        server.verify();
    }
    @Test void rejectsWorkerErrorsAndNonAudioResponses() {
        for (String body : new String[]{"{}", "not audio"}) {
            var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://127.0.0.1:8766/synthesize")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(AiClientException.class, () -> new LocalTtsClient(builder.build(), new LocalTtsProperties()).synthesize("hello"));
            server.verify();
        }
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8766/synthesize")).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(AiClientException.class, () -> new LocalTtsClient(builder.build(), new LocalTtsProperties()).synthesize("hello"));
    }
    @Test void rejectsRemoteEndpointsAndInvalidInput() {
        var properties = new LocalTtsProperties(); properties.setBaseUrl("https://remote.example");
        assertThrows(IllegalArgumentException.class, () -> new LocalTtsClient(RestClient.create(), properties));
        var client = new LocalTtsClient(RestClient.create(), new LocalTtsProperties());
        assertThrows(AiClientException.class, () -> client.synthesize("hello", Double.NaN));
        assertThrows(AiClientException.class, () -> client.synthesize(" "));
    }
}
