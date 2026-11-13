package community.likelion.growwithusserver.growth.service;

import community.likelion.growwithusserver.common.ai.LlmClient;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.growth.weather.GrowthWeatherReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrowthExplanationService {
    private final LlmClient llm;
    private final ObjectMapper mapper;
    private final GrowthWeatherReader weather;
    private final Map<String, String> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) { return size() > 128; }
    };

    // 성장 계산 트랜잭션과 잠금이 끝난 뒤에만 모델을 호출한다.
    public GrowthDtos.Detail explain(GrowthDtos.Detail detail) {
        if (detail.isSimulated() || weather.simulated() || TransactionSynchronizationManager.isActualTransactionActive()) return detail;
        try {
            String facts = mapper.writeValueAsString(Map.of(
                    "stage", detail.getStage().name(), "accumulatedGdd", detail.getAccumulatedGdd(),
                    "progress", detail.getProgress(), "expectedHarvestDate", String.valueOf(detail.getExpectedHarvestDate()),
                    "calibratedAt", String.valueOf(detail.getCalibratedAt())));
            String explanation;
            synchronized (cache) { explanation = cache.get(facts); }
            if (explanation == null) {
                String reply = llm.complete("""
                        기온으로 계산한 작물 성장 정보를 어르신도 이해하기 쉬운 한국어 두 문장으로 설명하세요.
                        입력은 계산된 사실입니다. 단계나 날짜, 수치, 관찰 결과를 새로 만들지 마세요.
                        수확일은 예상이며 실제 작물 상태는 관리사가 확인한다는 뜻을 포함하세요.
                        병을 진단하거나 약제와 비료를 추천하지 마세요.
                        JSON {"explanation":"300자 이내의 설명"}만 반환하세요.
                        """, facts);
                if (reply == null || reply.length() > 4000) return detail;
                var root = mapper.readTree(reply);
                var value = root == null ? null : root.get("explanation");
                if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 300) return detail;
                explanation = value.asText().strip();
                synchronized (cache) { cache.put(facts, explanation); }
            }
            detail.setExplanation(explanation);
        } catch (RuntimeException ex) {
            // 모델 오류는 계산 결과와 기존 설명을 훼손하지 않는다. 실패 응답은 캐시하지 않는다.
        }
        return detail;
    }
}
