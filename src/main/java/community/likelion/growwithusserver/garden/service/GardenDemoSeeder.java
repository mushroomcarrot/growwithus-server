package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 시연용 주인(minji), 관리사(soonja), 텃밭(B-14) 시드. 명세의 usr_minji, crt_soonja, gdn_b14에 해당한다.
 *
 * demo.enabled=true이고 demo.seed-password(DEMO_SEED_PASSWORD)가 8자 이상일 때만 넣는다.
 * 비밀번호를 정하지 않으면 계정을 만들지 않는다 (알려진 비밀번호의 계정이 생기지 않도록).
 * 이미 있는 계정이나 텃밭은 건드리지 않는다.
 */
@Slf4j
@Component
@Order(FarmSeeder.ORDER + 10)
@RequiredArgsConstructor
public class GardenDemoSeeder implements ApplicationRunner {
    public static final String OWNER_USERNAME = "minji";
    public static final String CARETAKER_USERNAME = "soonja";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FarmRepository farmRepository;
    private final PlotRepository plotRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final GardenRepository gardenRepository;
    private final EvolutionEventRepository evolutionEventRepository;

    @Value("${demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${demo.seed-password:}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demoEnabled || seedPassword == null || seedPassword.isBlank()) {
            return;
        }
        if (seedPassword.length() < MIN_PASSWORD_LENGTH) {
            log.warn("[run] 시연 계정 비밀번호가 {}자보다 짧아 시연 계정을 만들지 않음", MIN_PASSWORD_LENGTH);
            return;
        }

        Optional<Plot> demoPlot = farmRepository.findByExternalId(FarmSeeder.DEMO_FARM_EXTERNAL_ID)
                .map(Farm::getId)
                .flatMap(farmId -> plotRepository.findPlot(farmId, FarmSeeder.DEMO_PLOT_CODE));
        if (demoPlot.isEmpty()) {
            return;
        }
        Plot plot = demoPlot.get();

        User caretaker = seedUser(CARETAKER_USERNAME, "박순자", UserRole.CARETAKER);
        if (plot.getCaretaker() == null && caretaker.getRole() == UserRole.CARETAKER) {
            plot.setCaretaker(caretaker);
        }

        User owner = seedUser(OWNER_USERNAME, "김민지", UserRole.OWNER);
        if (owner.getRole() != UserRole.OWNER || plot.isAssigned() || ownerProfileRepository.existsByUserId(owner.getId())) {
            return;
        }
        seedGarden(owner, plot);
    }

    private User seedUser(String username, String name, UserRole role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            log.info("[seedUser] 시연 계정 생성: {} ({})", username, role);
            return userRepository.save(User.builder()
                    .username(username)
                    .name(name)
                    .password(passwordEncoder.encode(seedPassword))
                    .role(role)
                    .registeredAt(LocalDateTime.now())
                    .build());
        });
    }

    // 시연 시작 상태: 꽃 단계, 스트릭 12일, XP 1240, 수확 18일 전 (명세의 홈 예시와 같다)
    private void seedGarden(User owner, Plot plot) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        ownerProfileRepository.save(OwnerProfile.builder()
                .user(owner)
                .nickname("민지네")
                .district(District.SUJI)
                .createdAt(now)
                .updatedAt(now)
                .build());
        plot.setAssigned(true);
        Garden garden = gardenRepository.save(Garden.builder()
                .owner(owner)
                .plot(plot)
                .crop(plot.getCrop())
                .caretaker(plot.getCaretaker())
                .characterName(plot.getCrop().getCharacterName())
                .mood(CharacterMood.HAPPY)
                .startedOn(today.minusDays(60))
                .stage(GrowthStage.FLOWER)
                .accumulatedGdd(812.4)
                .expectedHarvestDate(today.plusDays(18))
                .streakDays(12)
                .lastCheeredOn(today.minusDays(1))
                .totalXp(1240)
                .createdAt(now)
                .build());
        owner.setProfileRegistered(true);

        // 지나온 단계 변화. 이미 확인한 것으로 넣어 일기에만 보이게 한다
        seedAckedEvent(garden, GrowthStage.SEED, GrowthStage.SPROUT, now.minusDays(45));
        seedAckedEvent(garden, GrowthStage.SPROUT, GrowthStage.FLOWER, now.minusDays(5));
        log.info("[seedGarden] 시연 텃밭 생성: {}", garden.getId());
    }

    private void seedAckedEvent(Garden garden, GrowthStage fromStage, GrowthStage toStage, LocalDateTime occurredAt) {
        evolutionEventRepository.save(EvolutionEvent.builder()
                .garden(garden)
                .fromStage(fromStage)
                .toStage(toStage)
                .occurredAt(occurredAt)
                .rewardXp(EvolutionEventService.rewardXp(toStage))
                .rewardBadgeCode(EvolutionEventService.rewardBadge(toStage).map(Enum::name).orElse(null))
                .acked(true)
                .ackedAt(occurredAt)
                .build());
    }
}
