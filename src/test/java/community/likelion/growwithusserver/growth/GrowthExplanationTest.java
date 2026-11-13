package community.likelion.growwithusserver.growth;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.common.ai.LlmClient;
import community.likelion.growwithusserver.growth.controller.GrowthBatchController;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.growth.service.GrowthExplanationService;
import community.likelion.growwithusserver.growth.weather.GrowthWeatherReader;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.PostMapping;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GrowthExplanationTest {
    private GrowthDtos.Detail detail() {
        return GrowthDtos.Detail.builder().stage(Stage.FLOWER).accumulatedGdd(100).progress(0.5)
                .explanation("fallback").build();
    }
    private GrowthExplanationService service(LlmClient llm) {
        return new GrowthExplanationService(llm, JsonMapper.builder().build(), (x,y,from,to) -> List.of());
    }
    @Test void generatesAndCachesExplanationWithoutChangingCalculatedFacts() {
        var calls = new AtomicInteger();
        var service = service((s,u) -> { calls.incrementAndGet(); assertTrue(u.contains("FLOWER")); return "{\"explanation\":\"꽃이 피는 단계예요. 실제 상태는 관리사가 확인해요.\"}"; });
        var result = service.explain(detail());
        assertTrue(result.getExplanation().contains("관리사"));
        assertEquals(Stage.FLOWER, result.getStage()); assertEquals(100, result.getAccumulatedGdd());
        service.explain(detail()); assertEquals(1, calls.get());
    }
    @Test void failuresRetainFallbackAndCanRetry() {
        var calls = new AtomicInteger();
        var service = service((s,u) -> { if (calls.incrementAndGet() == 1) throw new IllegalStateException(); return "{\"explanation\":\"다시 설명해요.\"}"; });
        assertEquals("fallback", service.explain(detail()).getExplanation());
        assertEquals("다시 설명해요.", service.explain(detail()).getExplanation());
        for (String reply : new String[]{"null", "{}", "bad json", "{\"explanation\":1}", "{\"explanation\":\" \"}"}) {
            assertEquals("fallback", service((s,u) -> reply).explain(detail()).getExplanation());
        }
    }
    @Test void neverCallsLlmWhileDatabaseTransactionIsActiveOrWeatherIsSimulated() {
        var service = service((s,u) -> { fail("model called while transaction is active"); return null; });
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertEquals("fallback", service.explain(detail()).getExplanation()); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        GrowthWeatherReader simulated = new GrowthWeatherReader() {
            public List<community.likelion.growwithusserver.ai.logic.GrowthCalculator.WeatherDay> read(int x,int y,java.time.LocalDate from,java.time.LocalDate to) { return List.of(); }
            public boolean simulated() { return true; }
        };
        assertEquals("fallback", new GrowthExplanationService((s,u) -> { fail("simulation called model"); return null; },
                JsonMapper.builder().build(), simulated).explain(detail()).getExplanation());
    }
    @Test void acceptsSpecifiedBatchPathAndKeepsExistingClientsWorking() throws Exception {
        var mapping = GrowthBatchController.class.getMethod("simulate", community.likelion.growwithusserver.care.data.dto.TaskDtos.BatchRequest.class).getAnnotation(PostMapping.class);
        assertTrue(List.of(mapping.value()).contains("/internal/ai/growth-simulation"));
        assertTrue(List.of(mapping.value()).contains("/internal/ai/simulate-growth"));
    }
}
