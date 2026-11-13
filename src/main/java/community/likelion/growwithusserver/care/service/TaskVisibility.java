package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class TaskVisibility {
    private final CareGardenReader gardens;
    private final TaskContextFactory contexts;
    public record Visible(List<Task> tasks, Set<TaskRules.Type> skippedTypes, String notice) {}
    public Visible select(String username, List<Task> tasks, LocalDate date) {
        var visible = new ArrayList<Task>(); var skipped = new LinkedHashSet<TaskRules.Type>();
        var notices = new LinkedHashSet<String>();
        for (Long gardenId : tasks.stream().map(Task::getGardenId).distinct().toList()) {
            var found = gardens.findGarden(gardenId);
            if (found.isEmpty() || !username.equals(found.get().caretakerUsername())) continue;
            var assigned = tasks.stream().filter(t -> t.getArchiveNumber() == 0 && t.getGardenId().equals(gardenId) && username.equals(t.getCaretakerUsername())).toList();
            var checked = new TaskRules().validate(contexts.create(found.get(), date), assigned.stream()
                    .filter(t -> t.getStatus() == TaskStatus.TODO).map(Task::getType).toList(), 120);
            var allowed = checked.tasks().stream().map(t -> t.type()).toList();
            visible.addAll(assigned.stream().filter(t -> t.getStatus() == TaskStatus.DONE || allowed.contains(t.getType())).toList());
            skipped.addAll(checked.skippedTypes()); notices.add(checked.weatherNotice());
        }
        return new Visible(List.copyOf(visible), Set.copyOf(skipped), String.join(" ", notices));
    }
}
