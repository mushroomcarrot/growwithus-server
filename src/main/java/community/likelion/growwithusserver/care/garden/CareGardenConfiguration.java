package community.likelion.growwithusserver.care.garden;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CareGardenConfiguration {
    @Bean
    @ConditionalOnMissingBean(CareGardenReader.class)
    CareGardenReader unconfiguredCareGardenReader() {
        return new CareGardenReader() {
            public List<Garden> findAssigned(String username) { return List.of(); }
            public List<Garden> findAll() { return List.of(); }
            public Optional<Garden> findGarden(Long id) { return Optional.empty(); }
            public Optional<Garden> findGardenForUpdate(Long id) { return Optional.empty(); }
        };
    }
}
