package community.likelion.growwithusserver.common.file;
import community.likelion.growwithusserver.support.FakeRepository;
import community.likelion.growwithusserver.voice.data.repository.VoiceMemoRepository;
import community.likelion.growwithusserver.voice.service.*;
import community.likelion.growwithusserver.voice.task.VoiceTaskReader;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 Spring 트랜잭션 완료 콜백 검사. DB 자체의 커밋과 롤백을 검증하는 테스트는 아니다. */
class UploadRollbackTest {
    private final List<String> removed = new ArrayList<>();
    private boolean deleteFails;
    private final FileUploadService upload = new FileUploadService(new FileStorage() {
        public String store(String dir, String name, InputStream data, long size, String type) { throw new AssertionError(); }
        public void deleteAudio(String url) { if (deleteFails) throw new IllegalStateException("storage unavailable"); removed.add(url); }
    });
    private static final String URL = "http://localhost/uploads/voice-memos/00000000-0000-0000-0000-000000000000.webm";
    private static final class Manager extends AbstractPlatformTransactionManager {
        boolean uncertainCommit;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) { if (uncertainCommit) throw new TransactionSystemException("commit outcome unknown"); }
        protected void doRollback(DefaultTransactionStatus status) {}
    }
    @Test void successfulCommitPreservesTheRetryFile() {
        new TransactionTemplate(new Manager()).executeWithoutResult(status -> upload.deleteAudioOnRollback(URL));
        assertTrue(removed.isEmpty());
    }
    @Test void confirmedRollbackDeletesOnlyThatNewFile() {
        new TransactionTemplate(new Manager()).executeWithoutResult(status -> { upload.deleteAudioOnRollback(URL); status.setRollbackOnly(); });
        assertEquals(List.of(URL), removed);
    }
    @Test void unknownCommitOutcomePreservesPotentiallyReferencedAudio() {
        var manager = new Manager(); manager.uncertainCommit = true;
        assertThrows(TransactionSystemException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> upload.deleteAudioOnRollback(URL)));
        assertTrue(removed.isEmpty());
    }
    @Test void cleanupFailureDoesNotReplaceTheBusinessError() {
        deleteFails = true; var expected = new IllegalArgumentException("business failure");
        var actual = assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(new Manager()).executeWithoutResult(status -> {
            upload.deleteAudioOnRollback(URL); throw expected;
        }));
        assertSame(expected, actual);
    }
    @Test void noTransactionNeverDeletesAnExistingFile() {
        upload.deleteAudioOnRollback(URL); assertTrue(removed.isEmpty());
    }
    @Test void pendingMemoSaveFailureRegistersCleanupBeforeTheSave() {
        var rows = FakeRepository.of(VoiceMemoRepository.class, (method, args) -> { throw new IllegalStateException("database write failed"); });
        var access = new VoiceTaskAccess(new VoiceTaskReader() {
            public Optional<VoiceTask> findTask(Long id) { return Optional.of(new VoiceTask(id, "care", false)); }
            public Optional<VoiceTask> findTaskForUpdate(Long id) { return findTask(id); }
        });
        var store = new VoiceMemoStore(rows, access, upload);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(new Manager()).executeWithoutResult(status -> store.createPending("care", 1L, URL)));
        assertEquals(List.of(URL), removed);
    }
}
