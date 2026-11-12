package community.likelion.growwithusserver.growth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component @RequiredArgsConstructor @Slf4j
public class GrowthScheduler {
    private final GrowthBatchService growth;
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void simulate() {
        var result = growth.run(LocalDate.now(), List.of());
        log.info("[simulate] 성장 계산 처리 수: {}, 실패 수: {}", result.updated(), result.failed());
    }
}
