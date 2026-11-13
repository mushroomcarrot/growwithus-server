package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.ai.service.TaskPlanningService;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class TaskGenerationService {
    private final TaskRepository tasks;
    private final CareGardenReader gardens;
    private final TaskContextFactory contexts;
    private final TaskPlanningService planner;
    private final TaskGenerationStore store;

    public TaskGenerationStore.Generated generateInitial(Long gardenId) {
        var garden = gardens.findGarden(gardenId).orElse(null);
        if (garden == null || garden.caretakerUsername() == null || garden.caretakerUsername().isBlank()) {
            return new TaskGenerationStore.Generated(0, 0);
        }
        var candidates = garden.guidance().stream().map(TaskRules.Guidance::type).toList();
        // The normal store rechecks ownership, weather and the daily budget, without an LLM request.
        return store.save(garden.caretakerUsername(), LocalDate.now(),
                List.of(new TaskGenerationStore.Prepared(gardenId, candidates, true)));
    }

    public TaskGenerationStore.Generated generate(String username, LocalDate date) {
        return generate(username, date, java.util.Set.of());
    }

    private TaskGenerationStore.Generated generate(String username, LocalDate date, java.util.Set<Long> selected) {
        if (!LocalDate.now().equals(date)) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        var prepared = new ArrayList<TaskGenerationStore.Prepared>();
        var today = tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc(username, date);
        // 모델 호출 중에는 DB 행 잠금을 유지하지 않는다. 저장 단계에서 다시 검사한다.
        for (var garden : gardens.findAssigned(username)) {
            if (!selected.isEmpty() && !selected.contains(garden.gardenId())) continue;
            if (!username.equals(garden.caretakerUsername())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
            if (tasks.existsByGardenIdAndDate(garden.gardenId(), date)) {
                var existing = today.stream().filter(t -> t.getGardenId().equals(garden.gardenId())).toList();
                var pending = existing.stream().filter(t -> t.getStatus() == TaskStatus.TODO)
                        .map(t -> t.getType()).toList();
                var rules = new TaskRules();
                if (rules.needsObservation(contexts.create(garden, date), pending,
                        existing.stream().anyMatch(t -> t.getType() == TaskRules.Type.CHECK))) {
                    prepared.add(new TaskGenerationStore.Prepared(garden.gardenId(),
                            List.of(TaskRules.Type.CHECK), true));
                }
                continue;
            }
            var result = planner.plan(contexts.create(garden, date), 120);
            prepared.add(new TaskGenerationStore.Prepared(garden.gardenId(), result.plan().tasks().stream()
                    .map(t -> t.type()).toList(), result.fallbackUsed()));
        }
        return prepared.isEmpty() ? new TaskGenerationStore.Generated(0, 0) : store.save(username, date, prepared);
    }

    public TaskGenerationStore.Generated generateAll(LocalDate date) {
        return generateAll(date, java.util.List.of());
    }

    public TaskGenerationStore.Generated generateAll(LocalDate date, java.util.List<String> gardenIds) {
        if (!LocalDate.now().equals(date) || gardenIds.size() > 200) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        var selected = gardenIds.stream().map(community.likelion.growwithusserver.common.id.IdPrefix.GARDEN::parse)
                .collect(java.util.stream.Collectors.toSet());
        var all = gardens.findAll();
        if (!all.stream().map(CareGardenReader.Garden::gardenId).collect(java.util.stream.Collectors.toSet()).containsAll(selected)) {
            throw new VoiceMemoException(ErrorCode.DATA_NOT_FOUND);
        }
        int generated = 0;
        int fallback = 0;
        for (String username : all.stream().filter(g -> selected.isEmpty() || selected.contains(g.gardenId()))
                .map(CareGardenReader.Garden::caretakerUsername).distinct().toList()) {
            var result = generate(username, date, selected);
            generated += result.generated(); fallback += result.fallback();
        }
        return new TaskGenerationStore.Generated(generated, fallback);
    }
}
