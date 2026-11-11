package community.likelion.growwithusserver.harvest.garden;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

// 텃밭 도메인의 구현체가 등록되기 전까지 쓰는 기본 구현. 어떤 텃밭도 찾지 못하므로 수확, 방문 API는 C404로 응답한다.
@Slf4j
@Configuration
public class HarvestGardenConfiguration {

    @Bean
    @ConditionalOnMissingBean(HarvestGardenReader.class)
    public HarvestGardenReader unconfiguredHarvestGardenReader() {
        return gardenId -> {
            log.warn("[findGarden] 텃밭 조회 구현체가 등록되지 않아 조회 실패 처리: {}", gardenId);
            return Optional.empty();
        };
    }
}
