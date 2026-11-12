package community.likelion.growwithusserver.demo.local;

import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.growth.service.GrowthService;
import community.likelion.growwithusserver.weather.service.DemoWeatherStore;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component @Profile("local-demo") @RequiredArgsConstructor
public class LocalDemoSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final CaretakerProfileRepository caretakers;
    private final DemoWeatherStore weather;
    private final GrowthService growth;
    @Value("${local-demo.password}") private String password;
    @Value("${file.base-url}") private String fileBaseUrl;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (password == null || password.length() < 16) throw new IllegalStateException("시연 비밀번호는 16자 이상으로 설정해 주세요.");
        seed(LocalDemoData.OWNER, "민지 가족", UserRole.OWNER);
        User caretaker = seed(LocalDemoData.CARETAKER, "순자 할머니", UserRole.CARETAKER);
        seed(LocalDemoData.ADMIN, "시연 운영자", UserRole.ADMIN);
        if (!caretakers.existsByUsername(LocalDemoData.CARETAKER)) {
            caretakers.save(CaretakerProfile.builder().userId(caretaker.getId()).username(LocalDemoData.CARETAKER)
                    .name("순자 할머니").farmId(1L).avatarUrl(fileBaseUrl.replaceAll("/$", "") + "/images/caretaker-default.svg")
                    .portraitConsent(false).createdAt(LocalDateTime.now()).build());
        }
        weather.set(WeatherCondition.SUNNY);
        growth.refreshSystem(1L);
    }

    private User seed(String username, String name, UserRole role) {
        var existing = users.findByUsername(username);
        if (existing.isPresent()) {
            var user = existing.get();
            if (user.getRole() != role || !passwords.matches(password, user.getPassword())) {
                throw new IllegalStateException("같은 이름의 다른 계정이 있어 시연 초기화를 중단했어요. 전용 DB와 기존 설정 파일을 확인해 주세요.");
            }
            user.setProfileRegistered(true);
            return user;
        }
        return users.save(User.builder().username(username).name(name).password(passwords.encode(password)).role(role)
                .profileRegistered(true).registeredAt(LocalDateTime.now()).build());
    }
}
