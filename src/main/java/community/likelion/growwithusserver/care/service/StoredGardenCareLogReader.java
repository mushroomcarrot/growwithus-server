package community.likelion.growwithusserver.care.service;
import community.likelion.growwithusserver.care.data.entity.CareLog;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.garden.care.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@Component @RequiredArgsConstructor @Transactional(readOnly = true)
public class StoredGardenCareLogReader implements GardenCareLogReader {
    private final CareLogRepository logs;
    private final TaskRepository tasks;
    public Optional<GardenCareLog> findCareLog(Long id) { return logs.findById(id).map(this::map); }
    public Optional<GardenCareLog> findLatest(Long id) {
        return logs.findFirstByGardenIdOrderByCreatedAtDescIdDesc(id).map(this::map);
    }
    public List<GardenCareLog> findByGarden(Long id, LocalDate from, LocalDate to) {
        return logs.findAllByGardenIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                id, from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream().map(this::map).toList();
    }
    public int countByGarden(Long id) { return (int) Math.min(Integer.MAX_VALUE, logs.countByGardenId(id)); }
    private GardenCareLog map(CareLog log) {
        // 한 기록에 묶인 작업을 모두 담는다. 묶음 정보가 없는 옛 기록은 처음 작업 하나만 쓴다.
        var types = tasks.findAllByCareLogIdOrderByIdAsc(log.getId()).stream().map(t -> t.getType().name()).distinct().toList();
        if (types.isEmpty()) types = tasks.findById(log.getTaskId()).map(t -> List.of(t.getType().name())).orElse(List.of());
        return new GardenCareLog(log.getId(), log.getGardenId(), log.getCaretakerUsername(), types,
                log.getSummary(), log.getPhotoUrl(), log.getCreatedAt(), log.getTranscript());
    }
}
