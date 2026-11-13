package community.likelion.growwithusserver.ai;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.ai.service.TaskPlanningService;
import community.likelion.growwithusserver.ai.service.VoiceMemoAnalysisService;
import community.likelion.growwithusserver.common.ai.AiClientException;
import community.likelion.growwithusserver.common.ai.LlmClient;
import community.likelion.growwithusserver.common.ai.LocalLlmProperties;
import community.likelion.growwithusserver.common.ai.OllamaLlmClient;
import community.likelion.growwithusserver.common.ai.SttClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 실제 모델 없이 AI 출력과 HTTP 경계를 검사한다. */
public class AiServiceSelfTest {
    private static int checks;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static void main(String[] args) {
        plans();
        voices();
        localClient();
        System.out.println("AI service checks passed: " + checks);
    }

    private static void plans() {
        TaskRules.Context context = new TaskRules.Context(LocalDate.of(2026, 11, 12), Stage.FLOWER,
                true, true, List.of(new TaskRules.Guidance(TaskRules.Type.SUPPORT,
                        Set.of(Stage.FLOWER), 2, "검토된 제목", "검토된 안내", 10, "test:source")), List.of());
        var good = new TaskPlanningService(reply("{\"tasks\":[{\"type\":\"SUPPORT\",\"title\":\"모델이 만든 문구\"}]}"), MAPPER)
                .plan(context, 120);
        check(!good.fallbackUsed() && good.plan().tasks().getFirst().title().equals("검토된 제목"),
                "모델 문구 대신 검토된 문구 사용");
        check(good.plan().tasks().getFirst().sourceId().equals("test:source"), "작업 근거 보존");
        var rain = new TaskPlanningService(reply("{\"tasks\":[{\"type\":\"WATER\"}]}"), MAPPER).plan(context, 120);
        check(rain.plan().tasks().getFirst().type() == TaskRules.Type.CHECK, "모델이 물 주기를 제안해도 제거");
        for (String invalid : List.of("not json", "null", "{}", "{\"tasks\":null}",
                "{\"tasks\":[{\"type\":\"SPRAY_PESTICIDE\"}]}")) {
            check(new TaskPlanningService(reply(invalid), MAPPER).plan(context, 120).fallbackUsed(),
                    "잘못된 모델 출력은 대체 처리");
        }
        LlmClient unavailable = (system, input) -> { throw new AiClientException(); };
        check(new TaskPlanningService(unavailable, MAPPER).plan(context, 120).fallbackUsed(), "모델 미연결 대체 처리");
    }

    private static void voices() {
        SttClient stt = (audio, filename) -> "잎에 점이 있어요";
        var good = new VoiceMemoAnalysisService(stt, reply("""
                {"summary":"잎에 점이 보여요.","issues":[{"code":"LEAF_SPOT","evidence":"잎에 점"}]}
                """), MAPPER).transcribeAndAnalyze(new byte[] {1}, "recording.webm");
        check(good.issues().equals(List.of(VoiceAnalysis.Issue.LEAF_SPOT)) && !good.fallbackUsed(), "관찰 근거와 코드 추출");
        check(good.transcript().equals("잎에 점이 있어요"), "STT 원문 보존");
        var hallucinated = new VoiceMemoAnalysisService(stt, reply("""
                {"summary":"벌레가 있어요","issues":[{"code":"PEST_SUSPECTED","evidence":"벌레"}]}
                """), MAPPER).analyze("잎에 점이 있어요");
        check(hallucinated.fallbackUsed() && hallucinated.issues().isEmpty(), "원문에 없는 증거 거부");
        var invalid = new VoiceMemoAnalysisService(stt, reply("{\"summary\":\"\",\"issues\":[]}"), MAPPER)
                .analyze("오늘 상태를 확인했어요");
        check(invalid.fallbackUsed() && invalid.summary().equals(invalid.transcript()), "빈 요약이면 원문 사용");
        var labels = new VoiceMemoAnalysisService(stt, reply("{}"), MAPPER).issueLabels();
        check(labels.size() == 5 && labels.get("LEAF_SPOT").equals("잎에 점이 있어요"), "코드 한글 목록");
        SttClient failed = (audio, filename) -> { throw new AiClientException(); };
        boolean failedHonestly = false;
        try { new VoiceMemoAnalysisService(failed, reply("{}"), MAPPER).transcribeAndAnalyze(new byte[] {1}, "a.wav"); }
        catch (AiClientException expected) { failedHonestly = true; }
        check(failedHonestly, "STT 실패 시 가짜 원문을 만들지 않음");
    }

    private static void localClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalLlmProperties settings = new LocalLlmProperties();
        settings.setModel("test-local");
        OllamaLlmClient client = new OllamaLlmClient(builder.build(), settings);
        server.expect(requestTo("http://127.0.0.1:11434/api/chat")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"message\":{\"content\":\"{}\"}}", MediaType.APPLICATION_JSON));
        check(client.complete("system", "input").equals("{}"), "로컬 응답 파싱");
        server.verify();
        server.reset();
        server.expect(requestTo("http://127.0.0.1:11434/api/chat")).andRespond(withServerError());
        boolean failed = false;
        try { client.complete("system", "input"); } catch (AiClientException expected) { failed = true; }
        check(failed, "로컬 HTTP 실패를 공통 AI 예외로 변환");
        server.verify();
        for (String address : List.of("https://paid.example", "http://example.com", "http://127.0.0.1@evil.example",
                "http://127.0.0.1/path", "http://localhost?target=remote")) {
            settings.setBaseUrl(address);
            boolean rejected = false;
            try { new OllamaLlmClient(builder.build(), settings); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "원격 주소 거부");
        }
        settings.setBaseUrl("http://127.0.0.1:11434");
        settings.setModel("test:cloud");
        boolean cloudRejected = false;
        try { new OllamaLlmClient(builder.build(), settings); } catch (IllegalArgumentException expected) { cloudRejected = true; }
        check(cloudRejected, "클라우드 모델 이름 거부");
    }

    private static LlmClient reply(String response) { return (system, input) -> response; }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
