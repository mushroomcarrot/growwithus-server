package community.likelion.growwithusserver.voice;

import community.likelion.growwithusserver.ai.service.VoiceMemoAnalysisService;
import community.likelion.growwithusserver.common.ai.AiClientException;
import community.likelion.growwithusserver.common.ai.LlmClient;
import community.likelion.growwithusserver.common.ai.SttClient;
import community.likelion.growwithusserver.common.file.FileUploadService;
import community.likelion.growwithusserver.common.response.GlobalException;
import community.likelion.growwithusserver.voice.data.dto.VoiceMemoConfirmRequestDto;
import community.likelion.growwithusserver.voice.data.entity.VoiceMemo;
import community.likelion.growwithusserver.voice.data.enums.VoiceMemoStatus;
import community.likelion.growwithusserver.voice.data.enums.VoiceProcessingStatus;
import community.likelion.growwithusserver.voice.data.repository.VoiceMemoRepository;
import community.likelion.growwithusserver.voice.service.VoiceMemoService;
import community.likelion.growwithusserver.voice.service.VoiceMemoStore;
import community.likelion.growwithusserver.voice.service.VoiceTaskAccess;
import community.likelion.growwithusserver.voice.task.VoiceTaskConfiguration;
import community.likelion.growwithusserver.voice.task.VoiceTaskReader;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/** 모델과 DB 대신 테스트 접점을 사용한다. 실제 저장소 잠금의 동시성 검사는 별도로 필요하다. */
public class VoiceMemoSelfTest {
    private static int checks;
    private static final String TRANSCRIPT = "오늘 물을 줬고 잎에 점이 있어요";
    private static final LlmClient LLM = (system, input) -> """
            {"summary":"물을 줬어요. 잎에 점이 보여요.","issues":[{"code":"LEAF_SPOT","evidence":"잎에 점"}]}
            """;

    public static void main(String[] args) throws Exception {
        lifecycle();
        rejection();
        retry();
        completedDuringAnalysis();
        System.out.println("Voice memo checks passed: " + checks);
    }

    private static void lifecycle() {
        Fixture f = new Fixture();
        var draft = f.service.create("caretaker", "tsk_1", audio()).getData();
        check(draft.getVoiceMemoId().equals("vm_1") && draft.getStatus() == VoiceMemoStatus.DRAFT, "초안 ID와 상태");
        check(draft.getTranscript().equals(TRANSCRIPT) && draft.getIssues().equals(List.of("LEAF_SPOT")), "원문과 코드 보존");
        check(f.files == 1 && f.rows.size() == 1, "파일과 초안 저장");
        check(f.lockedTaskReads >= 2, "원본 보존과 처리 결과 저장 시 작업 잠금 접점 사용");
        rejects("C002", () -> f.store.getConfirmedForTask("caretaker", 1L, 1L));
        var confirmed = f.service.confirm("caretaker", "vm_1", null).getData();
        check(confirmed.getStatus() == VoiceMemoStatus.CONFIRMED, "확정 상태");
        var linked = f.store.getConfirmedForTask("caretaker", 1L, 1L);
        check(linked.transcript().equals(TRANSCRIPT) && linked.issues().equals(List.of("LEAF_SPOT")), "일기 연결용 원문과 요약");
        f.rows.get(1L).setTaskId(2L);
        rejects("C403", () -> f.store.getConfirmedForTask("caretaker", 1L, 1L));
        f.rows.get(1L).setTaskId(1L);
        var time = f.rows.get(1L).getConfirmedAt();
        f.service.confirm("caretaker", "vm_1", null);
        check(f.rows.get(1L).getConfirmedAt().equals(time), "동일 확정 재시도 시 시각 불변");
        f.service.confirm("caretaker", "vm_1", new VoiceMemoConfirmRequestDto(draft.getSummary()));
        check(f.rows.get(1L).getIssueCodes().equals("LEAF_SPOT"), "같은 요약이면 관찰 코드 유지");
        rejects("C4095", () -> f.service.confirm("caretaker", "vm_1", new VoiceMemoConfirmRequestDto("확정 뒤 수정")));
        rejects("C403", () -> f.service.confirm("other", "vm_1", null));
        rejects("C404", () -> f.service.confirm("caretaker", "vm_999", null));
        rejects("C002", () -> f.service.confirm("caretaker", "vm_1", new VoiceMemoConfirmRequestDto("  ")));

        f.service.create("caretaker", "tsk_1", audio());
        f.service.confirm("caretaker", "vm_2", new VoiceMemoConfirmRequestDto("직접 확인해서 수정한 내용"));
        check(f.rows.get(2L).getTranscript().equals(TRANSCRIPT), "수정해도 원문 불변");
        check(f.rows.get(2L).getIssueCodes().isEmpty(), "수정된 문장에 이전 코드를 자동 전파하지 않음");
        check(f.rows.get(2L).getSummary().equals("직접 확인해서 수정한 내용"), "수정 요약 저장");
    }

    private static void rejection() {
        Fixture f = new Fixture();
        rejects("C403", () -> f.service.create("other", "tsk_1", audio()));
        check(f.sttCalls == 0 && f.files == 0, "담당자 검사가 인식과 저장보다 먼저");
        rejects("C404", () -> f.service.create("caretaker", "tsk_999", audio()));
        rejects("C002", () -> f.service.create("caretaker", "vm_1", audio()));
        rejects("C4001", () -> f.service.create("caretaker", "tsk_1",
                new MockMultipartFile("audio", "x.txt", "text/plain", new byte[] {1})));
        check(f.sttCalls == 0 && f.files == 0, "파일 검증 실패 시 인식과 저장 없음");
        f.completed = true;
        rejects("C4095", () -> f.service.create("caretaker", "tsk_1", audio()));
        f.completed = false;
        f.stt = (bytes, filename) -> { throw new AiClientException(); };
        var failed = f.service.create("caretaker", "tsk_1", audio()).getData();
        check(failed.getProcessingStatus() == VoiceProcessingStatus.STT_FAILED && failed.isRetryable(), "STT 실패 시 재시도 상태");
        check(f.files == 1 && f.rows.size() == 1 && failed.getTranscript() == null, "실패해도 파일과 초안 보존, 원문 조작 없음");
        Fixture moved = new Fixture();
        moved.stt = (bytes, filename) -> { moved.assignee = "reassigned"; return TRANSCRIPT; };
        rejects("C403", () -> moved.service.create("caretaker", "tsk_1", audio()));
        check(moved.rows.get(1L).getStatus() == VoiceMemoStatus.DRAFT, "인식 중 재배정되면 확정하지 않음");
        VoiceTaskAccess unconfigured = new VoiceTaskAccess(new VoiceTaskConfiguration().unconfiguredVoiceTaskReader());
        rejects("C404", () -> unconfigured.requireOpen(1L, "caretaker"));

        Fixture closed = new Fixture();
        closed.service.create("caretaker", "tsk_1", audio());
        closed.completed = true;
        rejects("C4095", () -> closed.service.confirm("caretaker", "vm_1", null));
        check(closed.rows.get(1L).getStatus() == VoiceMemoStatus.DRAFT, "거부된 확정은 상태 불변");
    }

    private static void retry() {
        Fixture f = new Fixture();
        f.stt = (bytes, filename) -> { throw new AiClientException(); };
        f.service.create("caretaker", "tsk_1", audio());
        rejects("C002", () -> f.service.confirm("caretaker", "vm_1", null));
        rejects("C403", () -> f.service.retry("other", "vm_1"));
        f.stt = (bytes, filename) -> TRANSCRIPT;
        var recovered = f.service.retry("caretaker", "vm_1").getData();
        check(recovered.getProcessingStatus() == VoiceProcessingStatus.READY && !recovered.isRetryable(), "저장한 음성으로 STT 재시도");
        check(f.rows.size() == 1 && f.files == 1 && f.audioReads == 1, "재시도 시 같은 메모와 파일 사용");
        rejects("C4095", () -> f.service.retry("caretaker", "vm_1"));
        Fixture llm = new Fixture();
        llm.llm = (s, u) -> { throw new AiClientException(); };
        var partial = llm.service.create("caretaker", "tsk_1", audio()).getData();
        check(partial.getProcessingStatus() == VoiceProcessingStatus.LLM_FAILED && partial.getTranscript().equals(TRANSCRIPT), "LLM 실패 시 원문 보존");
        llm.llm = LLM;
        llm.service.retry("caretaker", "vm_1");
        check(llm.sttCalls == 1 && llm.audioReads == 0, "LLM만 실패하면 음성 인식을 반복하지 않음");
        Fixture concurrent = new Fixture();
        var first = concurrent.store.createPending("caretaker", 1L, "http://localhost/uploads/voice-memos/test.webm");
        rejects("C4095", () -> concurrent.store.beginRetry("caretaker", 1L));
        concurrent.rows.get(1L).setProcessingStartedAt(java.time.LocalDateTime.now().minusMinutes(3));
        var second = concurrent.store.beginRetry("caretaker", 1L);
        check(!first.token().equals(second.token()), "중단된 처리 재개 시 시도 토큰 교체");
        var analysis = new community.likelion.growwithusserver.ai.logic.VoiceAnalysis.Result(TRANSCRIPT, "요약", List.of(), false);
        rejects("C4095", () -> concurrent.store.finish("caretaker", first, analysis));
        concurrent.store.finish("caretaker", second, analysis);
        check(concurrent.rows.get(1L).getProcessingStatus() == VoiceProcessingStatus.READY, "최신 시도만 결과 저장");
    }

    private static void completedDuringAnalysis() {
        Fixture stt = new Fixture();
        stt.stt = (bytes, filename) -> { stt.completed = true; throw new AiClientException(); };
        var failed = stt.service.create("caretaker", "tsk_1", audio()).getData();
        check(failed.getProcessingStatus() == VoiceProcessingStatus.STT_FAILED && !failed.isRetryable(),
                "완료된 작업에는 실행할 수 없는 재시도를 표시하지 않음");
        check(stt.files == 1 && stt.rows.size() == 1, "분석 중 작업이 끝나도 음성 파일과 메모 보존");
        rejects("C4095", () -> stt.service.retry("caretaker", "vm_1"));

        Fixture llm = new Fixture();
        llm.llm = (system, input) -> { llm.completed = true; throw new AiClientException(); };
        var partial = llm.service.create("caretaker", "tsk_1", audio()).getData();
        check(partial.getTranscript().equals(TRANSCRIPT) && !partial.isRetryable(),
                "작업 완료 후 도착한 원문은 보존하고 재시도는 막음");
        rejects("C4095", () -> llm.service.confirm("caretaker", "vm_1", null));
    }

    static MockMultipartFile audio() {
        return new MockMultipartFile("audio", "recording.webm", "audio/webm",
                new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1});
    }

    static final class Fixture {
        final Map<Long, VoiceMemo> rows = new HashMap<>();
        String assignee = "caretaker";
        boolean completed;
        int lockedTaskReads;
        int files;
        int sttCalls;
        int audioReads;
        SttClient stt = (bytes, filename) -> TRANSCRIPT;
        LlmClient llm = LLM;
        final VoiceMemoService service;
        final VoiceMemoStore store;

        Fixture() {
            VoiceMemoRepository repository = (VoiceMemoRepository) Proxy.newProxyInstance(
                    VoiceMemoRepository.class.getClassLoader(), new Class<?>[] {VoiceMemoRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> {
                            VoiceMemo memo = (VoiceMemo) args[0];
                            if (memo.getId() == null) memo.setId((long) rows.size() + 1);
                            rows.put(memo.getId(), memo);
                            yield memo;
                        }
                        case "findById", "findForUpdate" -> Optional.ofNullable(rows.get((Long) args[0]));
                        case "findConfirmedIssues" -> rows.values().stream().filter(m -> m.getStatus() == VoiceMemoStatus.CONFIRMED
                                && ((List<?>) args[0]).contains(m.getTaskId()) && !m.getConfirmedAt().isBefore((java.time.LocalDateTime) args[1])).toList();
                        case "findAccessById" -> {
                            VoiceMemo memo = rows.get((Long) args[0]);
                            if (memo == null) yield Optional.empty();
                            Long taskId = memo.getTaskId();
                            String caretaker = memo.getCaretakerUsername();
                            yield Optional.of(new VoiceMemoRepository.Access() {
                                @Override public Long getTaskId() { return taskId; }
                                @Override public String getCaretakerUsername() { return caretaker; }
                            });
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            VoiceTaskAccess access = new VoiceTaskAccess(new VoiceTaskReader() {
                @Override
                public Optional<VoiceTask> findTask(Long id) {
                    return id == 1 ? Optional.of(new VoiceTask(id, assignee, completed)) : Optional.empty();
                }
                @Override
                public Optional<VoiceTask> findTaskForUpdate(Long id) {
                    lockedTaskReads++;
                    return findTask(id);
                }
            });
            FileUploadService upload = new FileUploadService(new community.likelion.growwithusserver.common.file.FileStorage() {
                public String store(String directory, String filename, java.io.InputStream input, long size, String contentType) {
                    files++;
                    return "http://localhost/uploads/" + directory + "/" + filename;
                }
                public byte[] readAudio(String url) { audioReads++; return new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1}; }
            });
            VoiceMemoAnalysisService analysis = new VoiceMemoAnalysisService((bytes, filename) -> {
                sttCalls++;
                return stt.transcribe(bytes, filename);
            }, (system, input) -> llm.complete(system, input), JsonMapper.builder().build());
            store = new VoiceMemoStore(repository, access);
            service = new VoiceMemoService(access, upload, analysis, store);
        }
    }

    private static void rejects(String code, Runnable action) {
        try { action.run(); } catch (GlobalException expected) {
            check(expected.getErrorCode().getCode().equals(code), "오류 코드 " + code);
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
