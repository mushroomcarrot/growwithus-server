package community.likelion.growwithusserver.farm.scheduler;

import community.likelion.growwithusserver.farm.service.FarmSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmSyncScheduler {
    private final FarmSyncService farmSyncService;

    // 매주 월요일 03:00. 시간대는 JVM 기본(Asia/Seoul)
    @Scheduled(cron = "0 0 3 * * MON")
    public void run() {
        farmSyncService.sync();
    }
}
