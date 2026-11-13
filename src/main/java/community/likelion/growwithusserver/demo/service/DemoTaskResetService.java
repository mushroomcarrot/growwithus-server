package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.care.data.repository.TaskDayRepository;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.service.GardenDemoSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DemoTaskResetService {
    private final TaskDayRepository days;
    private final TaskRepository tasks;
    private final GardenRepository gardens;
    private static final Map<String, String> TARGETS = Map.of(
            "minji", "B-14", "demo_owner_b13", "B-13", "demo_owner_b15", "B-15");

    @Transactional
    public void archiveToday() {
        String username = GardenDemoSeeder.CARETAKER_USERNAME;
        LocalDate today = LocalDate.now();
        days.ensureDay(username, today);
        days.lockDay(username, today).orElseThrow();
        // 모든 작업을 먼저 잠근 뒤 텃밭을 잠근다. 같은 텃밭의 다른 작업 완료와 순서가 뒤집히지 않는다.
        var locked = tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc(username, today).stream()
                .map(item -> tasks.findForUpdate(item.getId()).orElseThrow()).toList();
        for (var task : locked) {
            var garden = gardens.findByIdForUpdate(task.getGardenId()).orElse(null);
            if (garden == null || garden.getCaretaker() == null
                    || !username.equals(garden.getCaretaker().getUsername())
                    || !FarmSeeder.DEMO_FARM_EXTERNAL_ID.equals(garden.getPlot().getFarm().getExternalId())
                    || !garden.getPlot().getPlotCode().equals(TARGETS.get(garden.getOwner().getUsername()))) continue;
            task.setArchiveNumber(task.getId());
        }
        tasks.flush();
    }
}
