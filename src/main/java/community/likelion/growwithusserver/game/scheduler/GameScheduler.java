package community.likelion.growwithusserver.game.scheduler;

import community.likelion.growwithusserver.game.service.LeagueSettleService;
import community.likelion.growwithusserver.game.service.StreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 시간대는 JVM 기본(Asia/Seoul)
@Component
@RequiredArgsConstructor
public class GameScheduler {
    private final StreakService streakService;
    private final LeagueSettleService leagueSettleService;

    // 매일 00:05. 어제 응원하지 않은 텃밭의 스트릭을 끊는다
    @Scheduled(cron = "0 5 0 * * *")
    public void checkStreaks() {
        streakService.check(null);
    }

    // 매일 20:00. 오늘 아직 응원하지 않은 주인에게 스트릭이 끊길 수 있다고 알린다
    @Scheduled(cron = "0 0 20 * * *")
    public void remindStreaks() {
        streakService.remind();
    }

    // 매주 월요일 00:10. 지난주 리그를 정산하고 이번 주 리그와 퀘스트를 만든다
    @Scheduled(cron = "0 10 0 * * MON")
    public void settleLeagues() {
        leagueSettleService.settle(null);
    }
}
