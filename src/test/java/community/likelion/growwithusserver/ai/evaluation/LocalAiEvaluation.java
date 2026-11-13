package community.likelion.growwithusserver.ai.evaluation;

import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.ai.service.VoiceMemoAnalysisService;
import community.likelion.growwithusserver.common.ai.*;
import community.likelion.growwithusserver.common.http.RestClientConfiguration;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 명시적 실행 전용. 합성 원문으로 운영과 같은 정리 서비스를 호출하며 STT 정확도는 측정하지 않는다. */
public final class LocalAiEvaluation {
    public record Example(String id, String transcript, List<VoiceAnalysis.Issue> expectedIssues) {}
    public record Outcome(String id, List<VoiceAnalysis.Issue> expected, List<VoiceAnalysis.Issue> predicted,
                          boolean fallback, long latencyMs, String summary) {}
    public record Metrics(int cases, int exactMatches, int fallbacks, int truePositives, int falsePositives,
                          int falseNegatives, double precision, double recall, double f1, long p50Ms, long p95Ms) {}

    public static Example[] examples() throws java.io.IOException {
        try (var input = LocalAiEvaluation.class.getResourceAsStream("/ai-evaluation/voice-cases.json")) {
            if (input == null) throw new IllegalStateException("평가 자료가 없어요.");
            var examples = JsonMapper.builder().build().readValue(input, Example[].class);
            var ids = new HashSet<String>();
            if (examples.length == 0 || examples.length > 50) throw new IllegalArgumentException("평가 자료 수를 확인해 주세요.");
            for (var example : examples) {
                if (example.id() == null || !ids.add(example.id()) || example.transcript() == null
                        || example.transcript().isBlank() || example.transcript().length() > 8000
                        || example.expectedIssues() == null || new HashSet<>(example.expectedIssues()).size() != example.expectedIssues().size()) {
                    throw new IllegalArgumentException("평가 자료의 ID, 원문과 정답을 확인해 주세요.");
                }
            }
            return examples;
        }
    }

    public static Metrics score(List<Outcome> outcomes) {
        if (outcomes.isEmpty()) throw new IllegalArgumentException("빈 평가 결과는 점수로 만들지 않아요.");
        int exact = 0, fallbacks = 0, tp = 0, fp = 0, fn = 0;
        for (var item : outcomes) {
            var expected = new HashSet<>(item.expected());
            var predicted = new HashSet<>(item.predicted());
            if (item.fallback()) fallbacks++;
            if (!item.fallback() && expected.equals(predicted)) exact++;
            for (var code : predicted) { if (expected.contains(code)) tp++; else fp++; }
            for (var code : expected) if (!predicted.contains(code)) fn++;
        }
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        var latency = outcomes.stream().mapToLong(Outcome::latencyMs).sorted().toArray();
        return new Metrics(outcomes.size(), exact, fallbacks, tp, fp, fn, precision, recall, f1,
                latency[(int) Math.ceil(latency.length * .50) - 1], latency[(int) Math.ceil(latency.length * .95) - 1]);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("평가 결과 파일 경로 하나를 지정해 주세요.");
        var properties = new LocalLlmProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("AI_LOCAL_BASE_URL", "http://127.0.0.1:11434"));
        properties.setModel(System.getenv().getOrDefault("AI_LOCAL_MODEL", "qwen2.5:3b"));
        var mapper = JsonMapper.builder().build();
        var http = new RestClientConfiguration().externalRestClient(Duration.ofSeconds(3), Duration.ofSeconds(90));
        var client = new OllamaLlmClient(http, properties);
        var outcomes = new ArrayList<Outcome>();
        var modelReplies = new LinkedHashMap<String, String>();
        for (var example : examples()) {
            LlmClient traced = (system, input) -> {
                String reply = client.complete(system, input);
                modelReplies.put(example.id(), reply);
                return reply;
            };
            var service = new VoiceMemoAnalysisService((audio, name) -> { throw new IllegalStateException("텍스트 평가에서 STT를 호출하지 않아요."); }, traced, mapper);
            long started = System.nanoTime();
            var result = service.analyze(example.transcript());
            outcomes.add(new Outcome(example.id(), example.expectedIssues(), result.issues(), result.fallbackUsed(),
                    (System.nanoTime() - started) / 1_000_000, result.summary()));
        }
        var metrics = score(outcomes);
        var report = Map.of("measuredAt", OffsetDateTime.now().toString(), "model", properties.getModel(),
                "dataset", "synthetic-korean-observations-v1", "sttMeasured", false,
                "metrics", metrics, "outcomes", outcomes, "modelReplies", modelReplies);
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println(mapper.writeValueAsString(metrics));
        System.out.println("Report: " + output);
        // 원문 대체 결과가 정상 태그와 같아도 모델 성공으로 세지 않는다.
        if (metrics.exactMatches() != metrics.cases()) System.exit(1);
    }
}
