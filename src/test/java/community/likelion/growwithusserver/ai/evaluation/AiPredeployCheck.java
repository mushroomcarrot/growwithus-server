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
    private boolean cloud;
    private boolean stopExternalCalls;
    private long lastExternalCall;
    private final Map<String, Object> samples = new LinkedHashMap<>();

    private void check(String name, Check check) {
        if (stopExternalCalls && !name.equals("tts_mp3") && !name.equals("voice_confirmed_link")) {
            results.put(name, Map.of("status", "not_run", "reason", "Earlier provider call failed; no automatic retries"));
            System.out.println("NOT RUN " + name); return;
        }
        long start = System.nanoTime();
        try {
            boolean external = name.startsWith("stt_") || name.startsWith("vision_")
                    || name.equals("voice_summary_from_recognized_audio") || name.equals("voice_explicit_leaf_spot")
                    || name.equals("growth_explanation");
            if (cloud && external) {
                long wait = 15000 - (System.currentTimeMillis() - lastExternalCall);
                if (wait > 0) Thread.sleep(wait);
                lastExternalCall = System.currentTimeMillis();
            }
            check.run();
            results.put(name, Map.of("status", "passed", "milliseconds", (System.nanoTime() - start) / 1000000));
            System.out.println("PASS " + name);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) { Thread.currentThread().interrupt(); stopExternalCalls = true; }
            if (cloud && ex instanceof AiClientException) stopExternalCalls = true;
            results.put(name, Map.of("status", "failed", "errorType", ex.getClass().getSimpleName()));
            System.out.println("FAIL " + name + ": " + ex.getClass().getSimpleName());
        }
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalStateException("AI check failed"); }

    static boolean matchesReference(String actual, String reference) {
        if (actual == null || reference == null) return false;
        String observed = actual.replaceAll("[^가-힣A-Za-z0-9]", "");
        String expected = reference.replaceAll("[^가-힣A-Za-z0-9]", "");
        if (expected.isEmpty() || observed.isEmpty()) return false;
        for (String detail : List.of("물", "흙", "잎", "점")) {
            if (expected.contains(detail) && !observed.contains(detail)) return false;
        }
        int[] previous = new int[observed.length() + 1];
        for (int j = 0; j < previous.length; j++) previous[j] = j;
        for (int i = 1; i <= expected.length(); i++) {
            int[] current = new int[observed.length() + 1]; current[0] = i;
            for (int j = 1; j <= observed.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (expected.charAt(i - 1) == observed.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[observed.length()] <= expected.length() * 0.35;
    }

    private static void preparePublicFixtures(Path directory) throws Exception {
        Files.createDirectories(directory);
        byte[] photo = Files.readAllBytes(directory.resolve("tomato.jpg"));
        require(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(photo))
                .equals("b31a3f11a711cf86c8fcafbb4eb243c9f5c37932"));
        try (var reference = AiPredeployCheck.class.getResourceAsStream("/ai-evaluation/synthetic-korean.wav")) {
            require(reference != null);
            Files.write(directory.resolve("sample.wav"), reference.readAllBytes());
        }
        for (String format : List.of("webm", "mp4", "m4a")) {
            var process = new ProcessBuilder("ffmpeg", "-y", "-nostdin", "-v", "error", "-i",
                    directory.resolve("sample.wav").toString(), "-c:a", format.equals("webm") ? "libopus" : "aac",
                    directory.resolve("sample." + format).toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try { require(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0); }
            finally { if (process.isAlive()) process.destroyForcibly(); }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && !(args.length == 3 && args[2].equals("--cloud")))
            throw new IllegalArgumentException("Fixture directory, report path and optional --cloud required");
        var directory = Path.of(args[0]);
        var output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        var runner = new AiPredeployCheck();
        runner.cloud = args.length == 3;
        if (runner.cloud) {
            runner.check("public_fixture_preparation", () -> preparePublicFixtures(directory));
            runner.stopExternalCalls = !"passed".equals(((Map<?, ?>) runner.results.get("public_fixture_preparation")).get("status"));
        }
        var mapper = JsonMapper.builder().build();
        var http = new RestClientConfiguration().externalRestClient(Duration.ofSeconds(3), Duration.ofSeconds(90));
        var sttProperties = new LocalSttProperties(); sttProperties.setEnabled(true);
        SttClient stt;
        LlmClient llm;
        VisionClient vision;
        TtsClient tts;
        var properties = new LocalLlmProperties(); properties.setEnabled(true);
        properties.setModel(System.getenv().getOrDefault("AI_LOCAL_MODEL", "qwen2.5:3b"));
        properties.setVisionModel(System.getenv().getOrDefault("AI_LOCAL_VISION_MODEL", "qwen3-vl:2b-instruct"));
        if (runner.cloud) {
            var gemini = new GeminiProperties();
            gemini.setApiKey(System.getenv().getOrDefault("GEMINI_API_KEY", ""));
            gemini.setModel(System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite"));
            var client = new GeminiClient(http, gemini, mapper, new FfmpegAudioNormalizer()::normalize);
            stt = client; llm = client; vision = client; tts = new OfflineTtsClient();
            properties.setModel(gemini.getModel()); properties.setVisionModel(gemini.getModel());
        } else {
            stt = new LocalWhisperSttClient(http, sttProperties);
            llm = new OllamaLlmClient(http, properties);
            vision = new OllamaVisionClient(http, properties, mapper);
            var ttsProperties = new LocalTtsProperties(); ttsProperties.setEnabled(true);
            tts = new LocalTtsClient(http, ttsProperties);
        }
        var analysis = new VoiceMemoAnalysisService(stt, llm, mapper);
        var transcripts = new LinkedHashMap<String, String>();
        String reference;
        try (var stream = AiPredeployCheck.class.getResourceAsStream("/ai-evaluation/synthetic-korean-reference.txt")) {
            require(stream != null); reference = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        runner.samples.put("reference", reference);
        for (String format : List.of("wav", "webm", "mp4", "m4a")) {
            runner.check("stt_" + format, () -> {
                String text = stt.transcribe(Files.readAllBytes(directory.resolve("sample." + format)), "sample." + format);
                runner.samples.put("transcript_" + format, text);
                require(text.codePoints().filter(c -> c >= 0xac00 && c <= 0xd7a3).count() >= 5);
                if (runner.cloud) require(matchesReference(text, reference));
                transcripts.put(format, text);
            });
        }
        var analyzed = new ArrayList<VoiceAnalysis.Result>();
        runner.check("voice_summary_from_recognized_audio", () -> {
            require(transcripts.containsKey("wav"));
            var result = analysis.analyze(transcripts.get("wav"));
            require(!result.fallbackUsed() && !result.summary().isBlank());
            require(result.issues().contains(VoiceAnalysis.Issue.LEAF_SPOT));
            analyzed.add(result); runner.samples.put("summary", result.summary());
        });
        runner.check("voice_confirmed_link", () -> {
            require(!analyzed.isEmpty());
            community.likelion.growwithusserver.voice.CloudVoiceFlowCheck.confirm(analyzed.getFirst());
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
            runner.samples.put("growthExplanation", detail.getExplanation());
        });
        runner.check("tts_mp3", () -> {
            var service = new community.likelion.growwithusserver.caretaker.service.TtsService(tts);
            byte[] audio = service.synthesize("오늘 잎과 흙을 살펴보세요.", 0.9);
            require(Arrays.equals(audio, service.synthesize("오늘 잎과 흙을 살펴보세요.", 0.9)));
            require(audio.length > 100); Files.write(output.resolveSibling("tts-review.mp3"), audio);
        });
        runner.check("vision_tomato_photo", () -> {
            var result = vision.check(Files.readAllBytes(directory.resolve("tomato.jpg")), "사진 속 토마토 열매 확인");
            require(result.isPresent() && result.get().matched());
            runner.samples.put("visionTomato", result.get().message());
        });
        runner.check("vision_unrelated_request", () -> {
            var result = vision.check(Files.readAllBytes(directory.resolve("tomato.jpg")), "사진 속 파란 자동차 확인");
            require(result.isPresent() && !result.get().matched());
            runner.samples.put("visionUnrelated", result.get().message());
        });
        boolean passed = runner.results.values().stream().allMatch(value -> "passed".equals(((Map<?, ?>) value).get("status")));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", passed ? "passed" : "failed"); report.put("checkedAt", OffsetDateTime.now().toString());
        report.put("model", properties.getModel()); report.put("visionModel", properties.getVisionModel());
        report.put("provider", runner.cloud ? "gemini" : "local");
        report.put("minimumCloudCallGapSeconds", runner.cloud ? 15 : 0);
        report.put("voiceStorageCheck", "in_memory_test_double_not_database");
        report.put("publicSyntheticSamples", runner.samples);
        report.put("humanVoiceValidated", false); report.put("ttsListeningValidated", false);
        report.put("productionValidated", false); report.put("checks", runner.results);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("Report: " + output);
        if (!passed) System.exit(1);
    }
}
