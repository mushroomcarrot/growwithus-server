package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.ai.service.TaskPlanningService;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;

@Service @RequiredArgsConstructor
public class TaskGenerationService {
    private final TaskRepository tasks;
    private final CareGardenReader gardens;
    private final TaskContextFactory contexts;
    private final TaskPlanningService planner;
    private final TaskGenerationStore store;

    public TaskGenerationStore.Generated generate(String username, LocalDate date) {
        return generate(username, date, java.util.Set.of());
    }

    private TaskGenerationStore.Generated generate(String username, LocalDate date, java.util.Set<Long> selected) {
        if (!LocalDate.now().equals(date)) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        var prepared = new ArrayList<TaskGenerationStore.Prepared>();
        // 모델 호출 중에는 DB 행 잠금을 유지하지 않는다. 저장 단계에서 다시 검사한다.
        for (var garden : gardens.findAssigned(username)) {
            if (!selected.isEmpty() && !selected.contains(garden.gardenId())) continue;
            if (!username.equals(garden.caretakerUsername())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
            if (tasks.existsByGardenIdAndDate(garden.gardenId(), date)) continue;
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
