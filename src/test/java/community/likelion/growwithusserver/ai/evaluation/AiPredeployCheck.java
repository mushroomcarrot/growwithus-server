package community.likelion.growwithusserver.ai.evaluation;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.VoiceAnalysis;
import community.likelion.growwithusserver.ai.service.VoiceMemoAnalysisService;
import community.likelion.growwithusserver.common.ai.*;
import community.likelion.growwithusserver.common.http.RestClientConfiguration;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.growth.service.GrowthExplanationService;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 실제 설치된 로컬 모델 검사. 사용자 녹음과 운영 DB는 사용하지 않는다. */
public final class AiPredeployCheck {
    @FunctionalInterface interface Check { void run() throws Exception; }
    private final Map<String, Object> results = new LinkedHashMap<>();

    private void check(String name, Check check) {
        long start = System.nanoTime();
        try {
            check.run();
            results.put(name, Map.of("status", "passed", "milliseconds", (System.nanoTime() - start) / 1000000));
            System.out.println("PASS " + name);
        } catch (Exception ex) {
            results.put(name, Map.of("status", "failed", "errorType", ex.getClass().getSimpleName()));
            System.out.println("FAIL " + name + ": " + ex.getClass().getSimpleName());
        }
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException("AI check failed"); }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Fixture directory and report path required");
        var directory = Path.of(args[0]);
        var output = Path.of(args[1]);
        var runner = new AiPredeployCheck();
        var mapper = JsonMapper.builder().build();
        var http = new RestClientConfiguration().externalRestClient(Duration.ofSeconds(3), Duration.ofSeconds(90));
        var sttProperties = new LocalSttProperties(); sttProperties.setEnabled(true);
        var stt = new LocalWhisperSttClient(http, sttProperties);
        var properties = new LocalLlmProperties(); properties.setEnabled(true);
        properties.setModel(System.getenv().getOrDefault("AI_LOCAL_MODEL", "qwen2.5:3b"));
        properties.setVisionModel(System.getenv().getOrDefault("AI_LOCAL_VISION_MODEL", "qwen3-vl:2b-instruct"));
        var llm = new OllamaLlmClient(http, properties);
        var analysis = new VoiceMemoAnalysisService(stt, llm, mapper);
        var transcripts = new LinkedHashMap<String, String>();
        for (String format : List.of("wav", "webm", "mp4", "m4a")) {
            runner.check("stt_" + format, () -> {
                String text = stt.transcribe(Files.readAllBytes(directory.resolve("sample." + format)), "sample." + format);
                require(text.codePoints().filter(c -> c >= 0xac00 && c <= 0xd7a3).count() >= 5);
                transcripts.put(format, text);
            });
        }
        runner.check("voice_summary_from_recognized_audio", () -> {
            require(transcripts.containsKey("wav"));
            var result = analysis.analyze(transcripts.get("wav"));
            require(!result.fallbackUsed() && !result.summary().isBlank());
        });
        runner.check("voice_explicit_leaf_spot", () -> {
            var result = analysis.analyze("오늘 잎과 흙을 살펴봤어요. 잎에 작은 점이 보여요.");
            require(!result.fallbackUsed() && result.issues().contains(VoiceAnalysis.Issue.LEAF_SPOT));
        });
        runner.check("growth_explanation", () -> {
            var service = new GrowthExplanationService(llm, mapper, (x, y, from, to) -> List.of());
            var detail = GrowthDtos.Detail.builder().stage(Stage.FLOWER).accumulatedGdd(100).progress(0.5)
                    .expectedHarvestDate(LocalDate.now().plusDays(18)).explanation("fallback").build();
            service.explain(detail);
            require(!"fallback".equals(detail.getExplanation()) && detail.getStage() == Stage.FLOWER
                    && detail.getAccumulatedGdd() == 100 && detail.getExpectedHarvestDate().equals(LocalDate.now().plusDays(18)));
        });
        runner.check("tts_mp3", () -> {
            var ttsProperties = new LocalTtsProperties(); ttsProperties.setEnabled(true);
            byte[] audio = new LocalTtsClient(http, ttsProperties).synthesize("오늘 잎과 흙을 살펴보세요.", 0.9);
            require(audio.length > 100); Files.write(output.resolveSibling("tts-review.mp3"), audio);
        });
        var vision = new OllamaVisionClient(http, properties, mapper);
        runner.check("vision_tomato_photo", () -> {
            var result = vision.check(Files.readAllBytes(directory.resolve("tomato.jpg")), "사진 속 토마토 열매 확인");
            require(result.isPresent() && result.get().matched());
        });
        runner.check("vision_unrelated_request", () -> {
            var result = vision.check(Files.readAllBytes(directory.resolve("tomato.jpg")), "사진 속 파란 자동차 확인");
            require(result.isPresent() && !result.get().matched());
        });
        boolean passed = runner.results.values().stream().allMatch(value -> "passed".equals(((Map<?, ?>) value).get("status")));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", passed ? "passed" : "failed"); report.put("checkedAt", OffsetDateTime.now().toString());
        report.put("model", properties.getModel()); report.put("visionModel", properties.getVisionModel());
        report.put("humanVoiceValidated", false); report.put("ttsListeningValidated", false);
        report.put("productionValidated", false); report.put("checks", runner.results);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("Report: " + output);
        if (!passed) System.exit(1);
    }
}
