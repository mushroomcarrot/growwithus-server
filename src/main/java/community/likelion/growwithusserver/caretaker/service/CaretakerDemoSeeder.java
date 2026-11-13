package community.likelion.growwithusserver.caretaker.service;

import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.service.GardenDemoSeeder;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 시연용 관리사 프로필 시드. 명세의 crt_soonja에 해당한다.
 *
 * GardenDemoSeeder가 만든 soonja 계정에 시연 농장 소속 프로필을 붙여, 로그인하면 바로 오늘 할 일(CARE_TODAY)로 가게 한다.
 * demo.enabled=true이고 soonja 계정이 CARETAKER일 때만 넣는다. 이미 프로필이 있으면 건드리지 않는다.
 */
@Slf4j
@Component
@Order(FarmSeeder.ORDER + 15)
@RequiredArgsConstructor
public class CaretakerDemoSeeder implements ApplicationRunner {
    public static final String CARETAKER_NAME = "박순자";

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final CaretakerProfileRepository profiles;

    @Value("${demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${file.base-url}")
    private String fileBaseUrl;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demoEnabled) {
            return;
        }

        User caretaker = userRepository.findByUsername(GardenDemoSeeder.CARETAKER_USERNAME).orElse(null);
        var farm = farmRepository.findByExternalId(FarmSeeder.DEMO_FARM_EXTERNAL_ID).orElse(null);
        if (caretaker == null || farm == null || caretaker.getRole() != UserRole.CARETAKER
                || profiles.existsByUsername(caretaker.getUsername())) {
            return;
        }

        profiles.save(CaretakerProfile.builder()
                .userId(caretaker.getId())
                .username(caretaker.getUsername())
                .name(CARETAKER_NAME)
                .farmId(farm.getId())
                .avatarUrl(CaretakerService.defaultAvatarUrl(fileBaseUrl))
                .portraitConsent(false)
                .createdAt(LocalDateTime.now())
                .build());
        caretaker.setProfileRegistered(true);
        log.info("[run] 시연 관리사 프로필 생성: {}", caretaker.getUsername());
    }
}
