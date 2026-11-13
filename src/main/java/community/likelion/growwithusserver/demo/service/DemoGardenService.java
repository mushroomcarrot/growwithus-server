package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository;

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
    private final GrowthStateRepository growthStates;

    public record Refreshed(String notice, int generated) {}

    @Transactional(readOnly = true)
    public void populate(DemoStateResponseDto state) {
        demoGarden().ifPresent(garden -> {
            state.setFastForwardDays(growthStates.findById(garden.getId())
                    .map(s -> s.getDemoDays() == null ? 0 : s.getDemoDays()).orElse(0));
            state.setStage(garden.getStage().name());
            state.setStageProgress(garden.getStage().getLevel() + "/4");
            state.setTodayTaskGardens((int) demoGardens().stream().filter(item -> visible(item).tasks().stream()
                    .anyMatch(task -> task.getStatus() == TaskStatus.TODO)).count());
        });
    }

    // 완료 기록과 업로드 참조를 보존하고 기존 작업 생성 및 날씨 검증을 재사용한다.
    @Transactional
    public Refreshed refreshTasks() {
        int count = 0;
        var notices = new java.util.LinkedHashSet<String>();
        for (Garden garden : demoGardens()) {
            if (garden.getCaretaker() == null) continue;
            count += generation.generateInitial(garden.getId()).generated();
            String notice = visible(garden).notice();
            if (notice != null && !notice.isBlank()) notices.add(notice);
        }
        return new Refreshed(notices.isEmpty() ? null : String.join(" ", notices), count);
    }

    private List<Garden> demoGardens() {
        var selected = new java.util.ArrayList<Garden>();
        demoGarden().ifPresent(selected::add);
        additional("demo_owner_b13", "B-13").ifPresent(selected::add);
        additional("demo_owner_b15", "B-15").ifPresent(selected::add);
        return selected;
    }

    private Optional<Garden> additional(String username, String plotCode) {
        return gardens.findByOwnerUsername(username)
                .filter(garden -> FarmSeeder.DEMO_FARM_EXTERNAL_ID.equals(garden.getPlot().getFarm().getExternalId())
                        && plotCode.equals(garden.getPlot().getPlotCode()));
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
