package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskDayRepository;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class TaskGenerationStore {
    private final TaskRepository tasks;
    private final TaskDayRepository days;
    private final TaskAccess access;
    private final TaskContextFactory contexts;

    public record Prepared(Long gardenId, List<TaskRules.Type> candidates, boolean fallbackUsed) {}
    public record Generated(int generated, int fallback) {}

    @Transactional
    public Generated save(String username, LocalDate date, List<Prepared> prepared) {
        days.ensureDay(username, date);
        days.lockDay(username, date).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        var today = new java.util.ArrayList<>(tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc(username, date));
        int reserved = today.stream().mapToInt(Task::getMinutes).sum();
        int generated = 0;
        int fallback = 0;
        for (Prepared item : prepared) {
            if (reserved >= 120) break;
            var garden = access.garden(username, item.gardenId(), true);
            var context = contexts.create(garden, date);
            var rules = new TaskRules();
            var candidates = item.candidates();
            if (tasks.existsByGardenIdAndDate(garden.gardenId(), date)) {
                var existing = today.stream().filter(t -> t.getGardenId().equals(garden.gardenId())).toList();
                var pending = existing.stream().filter(t -> t.getStatus() == TaskStatus.TODO).map(Task::getType).toList();
                if (!rules.needsObservation(context, pending, existing.stream().anyMatch(t -> t.getType() == TaskRules.Type.CHECK))) continue;
                candidates = List.of(TaskRules.Type.CHECK);
            }
            var plan = rules.validate(context, candidates, Math.max(0, 120 - reserved));
            if (plan.tasks().isEmpty()) continue;
            for (var planned : plan.tasks()) {
                var saved = tasks.save(Task.builder().gardenId(garden.gardenId()).caretakerUsername(username).date(date)
                        .type(planned.type()).title(planned.title()).description(planned.description())
                        .minutes(planned.minutes()).sourceId(planned.sourceId()).status(TaskStatus.TODO)
                        .createdAt(LocalDateTime.now()).build());
                today.add(saved);
            }
            reserved += plan.totalMinutes();
            generated++;
            if (item.fallbackUsed()) fallback++;
        }
        return new Generated(generated, fallback);
    }
}
