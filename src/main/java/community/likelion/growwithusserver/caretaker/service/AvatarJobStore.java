package community.likelion.growwithusserver.caretaker.service;

import community.likelion.growwithusserver.caretaker.data.dto.CaretakerDtos;
import community.likelion.growwithusserver.caretaker.data.entity.AvatarJob;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.enums.AvatarStatus;
import community.likelion.growwithusserver.caretaker.data.exception.CaretakerException;
import community.likelion.growwithusserver.caretaker.data.repository.AvatarJobRepository;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

// 아바타 작업의 DB 상태 변경. 그림 생성(외부 호출)은 트랜잭션 밖에서 하고 여기서는 결과만 기록한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarJobStore {
    // 이 시간이 지나도 PENDING이면 서버 재시작 등으로 끊긴 작업으로 보고 실패 처리한다
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final UserService users;
    private final CaretakerProfileRepository profiles;
    private final AvatarJobRepository jobs;

    // 초상권 동의를 기록하고 PENDING 작업을 만든다. 진행 중인 작업이 있으면 C4095.
    @Transactional
    public AvatarJob start(String username) {
        // 같은 사용자의 동시 요청이 작업을 두 개 만들지 못하게 사용자 행을 잠근다
        users.getUserForUpdate(username);
        CaretakerProfile profile = profile(username);

        LocalDateTime now = LocalDateTime.now();
        jobs.findFirstByUsernameOrderByIdDesc(username)
                .filter(job -> job.getStatus() == AvatarStatus.PENDING)
                .ifPresent(job -> {
                    if (!isStale(job, now)) {
                        log.error("[start] 진행 중인 아바타 작업이 있음: {}", username);
                        throw new CaretakerException(ErrorCode.ALREADY_PROCESSED);
                    }
                    job.setStatus(AvatarStatus.FAILED);
                    job.setFinishedAt(now);
                });

        profile.setPortraitConsent(true);
        profile.setPortraitConsentedAt(now);
        return jobs.save(AvatarJob.builder().username(username).status(AvatarStatus.PENDING).createdAt(now).build());
    }

    // 만든 그림을 프로필 아바타로 반영한다. 이미 끝난 작업이면 아무것도 하지 않는다.
    @Transactional
    public void complete(Long jobId, String avatarUrl) {
        jobs.findById(jobId).filter(job -> job.getStatus() == AvatarStatus.PENDING).ifPresent(job -> {
            job.setStatus(AvatarStatus.DONE);
            job.setAvatarUrl(avatarUrl);
            job.setFinishedAt(LocalDateTime.now());
            profiles.findByUsername(job.getUsername()).ifPresent(profile -> profile.setAvatarUrl(avatarUrl));
            log.info("[complete] 아바타 생성 완료: {}", job.getUsername());
        });
    }

    // 실패하면 프로필 아바타는 그대로 둔다 (처음이면 기본 아바타).
    @Transactional
    public void fail(Long jobId) {
        jobs.findById(jobId).filter(job -> job.getStatus() == AvatarStatus.PENDING).ifPresent(job -> {
            job.setStatus(AvatarStatus.FAILED);
            job.setFinishedAt(LocalDateTime.now());
            log.warn("[fail] 아바타 생성 실패, 기존 아바타 유지: {}", job.getUsername());
        });
    }

    @Transactional
    public CaretakerDtos.Avatar latest(String username) {
        CaretakerProfile profile = profile(username);
        AvatarJob job = jobs.findFirstByUsernameOrderByIdDesc(username).orElseThrow(() -> {
            log.error("[latest] 아바타 작업 조회 실패: {}", username);
            return new CaretakerException(ErrorCode.DATA_NOT_FOUND);
        });

        LocalDateTime now = LocalDateTime.now();
        if (job.getStatus() == AvatarStatus.PENDING && isStale(job, now)) {
            job.setStatus(AvatarStatus.FAILED);
            job.setFinishedAt(now);
        }
        return view(job, profile);
    }

    // PENDING이면 주소가 없고, DONE이면 만든 그림, FAILED면 지금 쓰는 아바타(처음이면 기본 아바타)를 준다
    public static CaretakerDtos.Avatar view(AvatarJob job, CaretakerProfile profile) {
        String avatarUrl = switch (job.getStatus()) {
            case PENDING -> null;
            case DONE -> job.getAvatarUrl();
            case FAILED -> profile.getAvatarUrl();
        };
        return CaretakerDtos.Avatar.builder()
                .avatarJobId(IdPrefix.AVATAR_JOB.format(job.getId()))
                .status(job.getStatus())
                .avatarUrl(avatarUrl)
                .build();
    }

    private CaretakerProfile profile(String username) {
        return profiles.findByUsername(username).orElseThrow(() -> {
            log.error("[profile] 관리사 프로필 조회 실패: {}", username);
            return new CaretakerException(ErrorCode.DATA_NOT_FOUND);
        });
    }

    private boolean isStale(AvatarJob job, LocalDateTime now) {
        return job.getCreatedAt().plus(STALE_AFTER).isBefore(now);
    }
}
