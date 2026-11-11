package community.likelion.growwithusserver.weather.scheduler;

import community.likelion.growwithusserver.weather.service.WeatherSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WeatherSyncScheduler {
    private final WeatherSyncService weatherSyncService;

    // 매시 15분. 초단기실황과 단기예보 모두 발표시각의 10분 이후에 받을 수 있다. 시간대는 JVM 기본(Asia/Seoul)
    @Scheduled(cron = "0 15 * * * *")
    public void run() {
        weatherSyncService.sync(List.of());
    }
}
