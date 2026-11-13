package community.likelion.growwithusserver.caretaker.service;

import community.likelion.growwithusserver.caretaker.data.dto.CaretakerDtos;
import community.likelion.growwithusserver.caretaker.data.entity.AvatarJob;
import community.likelion.growwithusserver.caretaker.data.enums.AvatarStatus;
import community.likelion.growwithusserver.caretaker.data.exception.CaretakerException;
import community.likelion.growwithusserver.common.ai.AvatarImageClient;
import community.likelion.growwithusserver.common.ai.ImagePreview;
import community.likelion.growwithusserver.common.file.FileUploadService;
import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 관리사 AI 아바타. 요청은 PENDING 작업만 만들고 바로 돌아오며, 그림은 뒤에서 만든다.
 *
 * 1. 초상권 동의가 없으면 C4003, 사진이 jpg, png 10MB 이하가 아니면 C4001.
 * 2. 올린 사진 원본은 저장하지 않는다. 줄인 사진만 메모리에 들고 있다가 생성이 끝나면 버린다.
 * 3. 이미지 모델이 없거나 실패하면 FAILED가 되고 프로필은 기존 아바타(처음이면 기본 아바타)를 그대로 쓴다.
 */
@Slf4j
@Service
public class AvatarService {
    public static final String DIRECTORY = "avatars";
    // 한 번에 하나씩 만들고, 기다리는 요청은 8개까지만 받는다 (작은 서버의 메모리 보호)
    private static final int QUEUE_SIZE = 8;

    private final AvatarJobStore store;
    private final FileUploadService upload;
    private final AvatarImageClient client;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public AvatarService(AvatarJobStore store, FileUploadService upload, AvatarImageClient client) {
        this(store, upload, client, newExecutor());
    }

    // 테스트에서 실행기를 바꿔 끼울 수 있다
    AvatarService(AvatarJobStore store, FileUploadService upload, AvatarImageClient client, Executor executor) {
        this.store = store;
        this.upload = upload;
        this.client = client;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService service ? service : null;
    }

    public GlobalResponse<CaretakerDtos.Avatar> request(String username, MultipartFile photo, Boolean portraitConsent) {
        if (!Boolean.TRUE.equals(portraitConsent)) {
            log.error("[request] 초상권 동의 없이 아바타 생성 요청: {}", username);
            throw new CaretakerException(ErrorCode.PORTRAIT_CONSENT_REQUIRED);
        }
        byte[] preview = shrink(upload.readImage(photo));

        AvatarJob job = store.start(username);
        try {
            executor.execute(() -> generate(job.getId(), preview));
        } catch (RejectedExecutionException e) {
            log.warn("[request] 아바타 생성 대기열이 가득 차 실패 처리: {}", username);
            store.fail(job.getId());
            return GlobalResponse.ok(store.latest(username));
        }

        return GlobalResponse.ok(CaretakerDtos.Avatar.builder()
                .avatarJobId(IdPrefix.AVATAR_JOB.format(job.getId()))
                .status(AvatarStatus.PENDING)
                .build());
    }

    public GlobalResponse<CaretakerDtos.Avatar> get(String username) {
        return GlobalResponse.ok(store.latest(username));
    }

    // 대기열에 쌓아 둘 사진을 작게 줄인다. 그림으로 읽히지 않으면 C4001.
    private byte[] shrink(byte[] image) {
        try {
            byte[] preview = ImagePreview.jpeg(image);
            if (preview != null) {
                return preview;
            }
        } catch (java.io.IOException | RuntimeException e) {
            // 아래에서 형식 오류로 처리한다
        }
        log.warn("[shrink] 읽을 수 없는 사진");
        throw new FileNotSupportedException();
    }

    private void generate(Long jobId, byte[] photo) {
        try {
            var image = client.generate(photo);
            if (image.isPresent()) {
                store.complete(jobId, upload.storeGeneratedPng(image.get(), DIRECTORY));
                return;
            }
        } catch (RuntimeException e) {
            log.error("[generate] 아바타 생성 중 오류: {}", e.getClass().getSimpleName());
        }
        try {
            store.fail(jobId);
        } catch (RuntimeException e) {
            // 기록하지 못해도 조회할 때 오래된 PENDING을 실패로 바꾼다
            log.error("[generate] 아바타 실패 기록 오류: {}", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }

    private static ExecutorService newExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_SIZE), task -> {
            Thread thread = new Thread(task, "avatar-generator");
            thread.setDaemon(true);
            return thread;
        });
    }
}
