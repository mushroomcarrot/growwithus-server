package community.likelion.growwithusserver.garden.care;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 돌봄 기록 도메인의 구현체가 등록되기 전까지 쓰는 기본 구현. 돌봄 기록이 하나도 없는 것처럼 동작한다.
@Configuration
public class GardenCareLogConfiguration {

    @Bean
    @ConditionalOnMissingBean(GardenCareLogReader.class)
    public GardenCareLogReader unconfiguredGardenCareLogReader() {
        return new GardenCareLogReader() {
            @Override
            public Optional<GardenCareLog> findCareLog(Long careLogId) {
                return Optional.empty();
            }

            @Override
            public Optional<GardenCareLog> findLatest(Long gardenId) {
                return Optional.empty();
            }

            @Override
            public List<GardenCareLog> findByGarden(Long gardenId, LocalDate from, LocalDate to) {
                return List.of();
            }

            @Override
            public int countByGarden(Long gardenId) {
                return 0;
            }
        };
    }
}
