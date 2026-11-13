package community.likelion.growwithusserver.care.scheduler;
import community.likelion.growwithusserver.care.service.TaskGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
@Component @RequiredArgsConstructor
public class DailyTaskScheduler {
    private final TaskGenerationService generation;
    @Scheduled(cron = "0 0 5 * * *")
    public void generate() { generation.generateAll(LocalDate.now()); }
}
