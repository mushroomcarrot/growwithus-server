package community.likelion.growwithusserver.growth.service;

import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class GrowthBatchService {
    private final CareGardenReader gardens;
    private final GrowthService growth;
    public record Result(int updated, int failed) {}
    public Result run(LocalDate date, List<String> gardenIds) {
        if (!LocalDate.now().equals(date) || gardenIds == null || gardenIds.size() > 200) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        var selected = gardenIds.stream().map(IdPrefix.GARDEN::parse).collect(java.util.stream.Collectors.toSet());
        var available = gardens.findAll();
        if (!available.stream().map(CareGardenReader.Garden::gardenId).collect(java.util.stream.Collectors.toSet()).containsAll(selected)) {
            throw new VoiceMemoException(ErrorCode.DATA_NOT_FOUND);
        }
        int updated = 0; int failed = 0;
        for (var garden : available) {
            if (!selected.isEmpty() && !selected.contains(garden.gardenId())) continue;
            try { growth.refreshSystem(garden.gardenId()); updated++; }
            catch (VoiceMemoException | IllegalArgumentException ex) { failed++; }
        }
        return new Result(updated, failed);
    }
}
