package community.likelion.growwithusserver.caretaker;

import community.likelion.growwithusserver.caretaker.data.entity.AvatarJob;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.enums.AvatarStatus;
import community.likelion.growwithusserver.caretaker.data.repository.AvatarJobRepository;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.caretaker.service.AvatarJobStore;
import community.likelion.growwithusserver.caretaker.service.AvatarService;
import community.likelion.growwithusserver.common.ai.AvatarImageClient;
import community.likelion.growwithusserver.common.file.FileStorage;
import community.likelion.growwithusserver.common.file.FileUploadService;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;
import community.likelion.growwithusserver.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarServiceTest {
    private static final String DEFAULT_AVATAR = "http://localhost:8080/images/caretaker-default.svg";

    private final CaretakerProfileRepository profiles = mock(CaretakerProfileRepository.class);
    private final AvatarJobRepository jobs = mock(AvatarJobRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final List<AvatarJob> saved = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private CaretakerProfile profile;

    @BeforeEach
    void setUp() {
        profile = CaretakerProfile.builder().id(7L).username("soonja").name("박순자").avatarUrl(DEFAULT_AVATAR).build();
        when(profiles.findByUsername("soonja")).thenReturn(Optional.of(profile));
        when(jobs.save(any())).thenAnswer(call -> {
            AvatarJob job = call.getArgument(0);
            job.setId(saved.size() + 1L);
            saved.add(job);
            return job;
        });
        when(jobs.findById(anyLong())).thenAnswer(call -> saved.stream()
                .filter(job -> job.getId().equals(call.getArgument(0))).findFirst());
        when(jobs.findFirstByUsernameOrderByIdDesc("soonja")).thenAnswer(call ->
                saved.isEmpty() ? Optional.empty() : Optional.of(saved.get(saved.size() - 1)));
        when(storage.store(eq("avatars"), anyString(), any(), anyLong(), eq("image/png")))
                .thenReturn("http://localhost:8080/uploads/avatars/made.png");
    }

    private AvatarService service(AvatarImageClient client, Executor executor) throws Exception {
        Constructor<AvatarService> constructor = AvatarService.class.getDeclaredConstructor(
                AvatarJobStore.class, FileUploadService.class, AvatarImageClient.class, Executor.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new AvatarJobStore(mock(UserService.class), profiles, jobs),
                new FileUploadService(storage), client, executor);
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private static MockMultipartFile photo() throws Exception {
        return new MockMultipartFile("photo", "face.png", "image/png", png());
    }

    @Test
    void 초상권_동의가_없으면_C4003이고_작업을_만들지_않는다() throws Exception {
        AvatarService service = service(image -> Optional.empty(), queued::add);

        for (Boolean consent : new Boolean[]{null, false}) {
            assertThatThrownBy(() -> service.request("soonja", photo(), consent))
                    .isInstanceOfSatisfying(GlobalException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PORTRAIT_CONSENT_REQUIRED));
        }
        verify(jobs, never()).save(any());
        assertThat(profile.isPortraitConsent()).isFalse();
    }

    @Test
    void 사진이_아니거나_읽을_수_없으면_C4001이다() throws Exception {
        AvatarService service = service(image -> Optional.empty(), queued::add);
        // PNG 서명만 있고 내용이 깨진 파일
        byte[] broken = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

        for (MockMultipartFile file : new MockMultipartFile[]{
                new MockMultipartFile("photo", "face.gif", "image/gif", new byte[]{'G', 'I', 'F'}),
                new MockMultipartFile("photo", "face.png", "image/png", broken)}) {
            assertThatThrownBy(() -> service.request("soonja", file, true))
                    .isInstanceOfSatisfying(GlobalException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_NOT_SUPPORTED));
        }
        assertThatThrownBy(() -> service.request("soonja", null, true)).isInstanceOf(GlobalException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void 요청은_PENDING으로_돌아오고_생성이_끝나면_프로필_아바타가_바뀐다() throws Exception {
        byte[] made = png();
        AvatarService service = service(image -> Optional.of(made), queued::add);

        var requested = service.request("soonja", photo(), true).getData();

        assertThat(requested.getAvatarJobId()).isEqualTo("avj_1");
        assertThat(requested.getStatus()).isEqualTo(AvatarStatus.PENDING);
        assertThat(requested.getAvatarUrl()).isNull();
        assertThat(profile.isPortraitConsent()).isTrue();
        assertThat(profile.getPortraitConsentedAt()).isNotNull();
        assertThat(service.get("soonja").getData().getStatus()).isEqualTo(AvatarStatus.PENDING);
        // 원본 사진은 저장하지 않는다
        verify(storage, never()).store(anyString(), anyString(), any(), anyLong(), anyString());

        queued.get(0).run();

        var done = service.get("soonja").getData();
        assertThat(done.getStatus()).isEqualTo(AvatarStatus.DONE);
        assertThat(done.getAvatarUrl()).isEqualTo("http://localhost:8080/uploads/avatars/made.png");
        assertThat(profile.getAvatarUrl()).isEqualTo("http://localhost:8080/uploads/avatars/made.png");
    }

    @Test
    void 생성에_실패하면_FAILED이고_기본_아바타를_그대로_쓴다() throws Exception {
        AvatarService empty = service(image -> Optional.empty(), Runnable::run);
        var failed = empty.request("soonja", photo(), true).getData();
        assertThat(failed.getStatus()).isEqualTo(AvatarStatus.PENDING);
        assertThat(empty.get("soonja").getData().getStatus()).isEqualTo(AvatarStatus.FAILED);
        assertThat(empty.get("soonja").getData().getAvatarUrl()).isEqualTo(DEFAULT_AVATAR);

        AvatarService broken = service(image -> { throw new IllegalStateException("provider"); }, Runnable::run);
        broken.request("soonja", photo(), true);
        assertThat(broken.get("soonja").getData().getStatus()).isEqualTo(AvatarStatus.FAILED);
        assertThat(profile.getAvatarUrl()).isEqualTo(DEFAULT_AVATAR);
    }

    @Test
    void 진행_중인_작업이_있으면_C4095이고_오래된_작업은_실패로_정리한다() throws Exception {
        AvatarService service = service(image -> Optional.empty(), queued::add);
        service.request("soonja", photo(), true);

        assertThatThrownBy(() -> service.request("soonja", photo(), true))
                .isInstanceOfSatisfying(GlobalException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_PROCESSED));

        // 서버 재시작 등으로 끊긴 작업: 5분이 지나면 실패로 보고 새 요청을 받는다
        saved.get(0).setCreatedAt(LocalDateTime.now().minus(AvatarJobStore.STALE_AFTER).minusSeconds(1));
        assertThat(service.get("soonja").getData().getStatus()).isEqualTo(AvatarStatus.FAILED);
        assertThat(service.request("soonja", photo(), true).getData().getAvatarJobId()).isEqualTo("avj_2");
    }

    @Test
    void 대기열이_가득_차면_바로_FAILED로_돌려준다() throws Exception {
        AvatarService service = service(image -> Optional.empty(), task -> { throw new RejectedExecutionException(); });

        var response = service.request("soonja", photo(), true).getData();

        assertThat(response.getStatus()).isEqualTo(AvatarStatus.FAILED);
        assertThat(response.getAvatarUrl()).isEqualTo(DEFAULT_AVATAR);
    }

    @Test
    void 프로필이나_요청_이력이_없으면_C404다() throws Exception {
        AvatarService service = service(image -> Optional.empty(), queued::add);

        assertThatThrownBy(() -> service.get("soonja"))
                .isInstanceOfSatisfying(GlobalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATA_NOT_FOUND));
        when(profiles.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request("nobody", photo(), true))
                .isInstanceOfSatisfying(GlobalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATA_NOT_FOUND));
    }
}
