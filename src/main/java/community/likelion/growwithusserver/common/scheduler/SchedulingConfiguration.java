package community.likelion.growwithusserver.common.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 활성화. scheduler.enabled=false면 스케줄러가 돌지 않고 /internal API로만 배치를 실행한다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
