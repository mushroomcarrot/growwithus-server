package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;

@Service @RequiredArgsConstructor
public class TodayTaskService {
    private final TaskGenerationService generation;
    private final TaskRepository tasks;
    private final CareGardenReader gardens;
    private final TaskVisibility visibility;
    private final community.likelion.growwithusserver.growth.service.GrowthStatusService growth;

    public GlobalResponse<TaskDtos.Today> today(String username) {
        LocalDate date = LocalDate.now();
        generation.generate(username, date);
        var all = tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc(username, date);
        var selection = visibility.select(username, all, date);
        var cards = new ArrayList<TaskDtos.GardenCard>();
        int minutes = 0;
        for (var garden : gardens.findAssigned(username)) {
            if (!username.equals(garden.caretakerUsername())) continue;
            var visible = selection.tasks().stream().filter(t -> t.getGardenId().equals(garden.gardenId())).toList();
            var expectedStage = growth.expectedCheck(garden);
            minutes += visible.stream().mapToInt(Task::getMinutes).sum();
            cards.add(TaskDtos.GardenCard.builder().gardenId(IdPrefix.GARDEN.format(garden.gardenId()))
                    .plotCode(garden.plotCode()).cropName(garden.cropName()).ownerNickname(garden.ownerNickname())
                    .tasks(visible.stream().map(t -> TaskDtos.Item.builder().taskId(IdPrefix.TASK.format(t.getId()))
                            .type(t.getType()).title(t.getTitle()).description(t.getDescription()).minutes(t.getMinutes()).status(t.getStatus()).build()).toList())
                    .stageCheck(TaskDtos.StageCheck.builder().required(expectedStage != null)
                            .expectedStage(expectedStage).build()).build());
        }
        return GlobalResponse.ok(TaskDtos.Today.builder().date(date).weatherNotice(selection.notice())
                .skippedTypes(java.util.List.copyOf(selection.skippedTypes())).totalMinutes(minutes).gardens(cards).build());
    }
}
