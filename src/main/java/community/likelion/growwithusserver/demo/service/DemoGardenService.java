package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.service.TaskGenerationService;
import community.likelion.growwithusserver.care.service.TaskVisibility;
import community.likelion.growwithusserver.demo.data.dto.DemoStateResponseDto;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.service.GardenDemoSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DemoGardenService {
    private final GardenRepository gardens;
    private final TaskRepository tasks;
    private final TaskGenerationService generation;
    private final TaskVisibility visibility;

    public record Refreshed(String notice, int generated) {}

    @Transactional(readOnly = true)
    public void populate(DemoStateResponseDto state) {
        demoGarden().ifPresent(garden -> {
            state.setStage(garden.getStage().name());
            state.setStageProgress(garden.getStage().getLevel() + "/4");
            state.setTodayTaskGardens(visible(garden).tasks().stream()
                    .anyMatch(task -> task.getStatus() == TaskStatus.TODO) ? 1 : 0);
        });
    }

    // 완료 기록과 업로드 참조를 보존하고 기존 작업 생성 및 날씨 검증을 재사용한다.
    @Transactional
    public Refreshed refreshTasks() {
        return demoGarden().map(garden -> {
            if (garden.getCaretaker() == null) return new Refreshed(null, 0);
            var generated = generation.generateInitial(garden.getId());
            return new Refreshed(visible(garden).notice(), generated.generated());
        }).orElseGet(() -> new Refreshed(null, 0));
    }

    private TaskVisibility.Visible visible(Garden garden) {
        if (garden.getCaretaker() == null) {
            return new TaskVisibility.Visible(List.of(), java.util.Set.of(), "");
        }
        String username = garden.getCaretaker().getUsername();
        LocalDate today = LocalDate.now();
        var selected = tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc(username, today).stream()
                .filter(task -> garden.getId().equals(task.getGardenId())).toList();
        return visibility.select(username, selected, today);
    }

    private Optional<Garden> demoGarden() {
        return gardens.findByOwnerUsername(GardenDemoSeeder.OWNER_USERNAME)
                .filter(garden -> FarmSeeder.DEMO_FARM_EXTERNAL_ID.equals(garden.getPlot().getFarm().getExternalId())
                        && FarmSeeder.DEMO_PLOT_CODE.equals(garden.getPlot().getPlotCode()));
    }
}
