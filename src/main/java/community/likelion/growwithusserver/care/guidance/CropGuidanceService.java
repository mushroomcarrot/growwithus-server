package community.likelion.growwithusserver.care.guidance;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.ai.logic.TaskRules.*;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class CropGuidanceService {
    private final CropRepository crops;
    private final CropGuidanceRepository settings;
    private final ObjectMapper mapper;
    public static final String TOMATO_SOURCE = "https://www.yongin.go.kr/home/atc/cityConsum/cityConsum05/cityConsum05_02/cityConsum05_02_04.jsp";

    public record Update(List<Guidance> rules) {
        public Update {
            if (rules == null || rules.size() > Type.values().length) throw new IllegalArgumentException("작업 지침 수를 확인해 주세요.");
            rules = List.copyOf(rules);
            Set<Type> types = new HashSet<>();
            for (var rule : rules) {
                URI source = URI.create(rule.sourceId());
                if (!types.add(rule.type()) || rule.minimumIntervalDays() > 365
                        || !"https".equals(source.getScheme()) || source.getHost() == null || source.getRawUserInfo() != null) {
                    throw new IllegalArgumentException("중복 작업이나 출처 주소를 확인해 주세요.");
                }
            }
        }
    }

    @Transactional(readOnly=true)
    public List<Guidance> read(Long cropId, String cropName) {
        return settings.findById(cropId).map(saved -> List.of(mapper.readValue(saved.getRulesJson(), Guidance[].class)))
                .orElseGet(() -> defaults(cropName));
    }

    @Transactional(readOnly=true)
    public Update get(Long cropId) {
        var crop = crops.findById(cropId).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        return new Update(read(cropId, crop.getName()));
    }

    @Transactional
    public Update replace(Long cropId, Update update) {
        if (update == null) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        crops.findByIdForUpdate(cropId).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        settings.save(CropGuidanceSettings.builder().cropId(cropId).rulesJson(mapper.writeValueAsString(update.rules()))
                .updatedAt(LocalDateTime.now()).build());
        return update;
    }

    public static List<Guidance> defaults(String name) {
        if (name == null || !Set.of("토마토", "방울토마토").contains(name.strip())) return List.of();
        // Interval and minutes are editable project planning defaults, not figures published by the source.
        return List.of(
                new Guidance(Type.WATER, Set.of(Stage.values()), 1, "흙을 살피고 물 주기",
                        "흙이 마른지 살펴보세요. 마른 경우에만 뿌리 쪽에 천천히 물을 주세요.", 10, TOMATO_SOURCE),
                new Guidance(Type.SUPPORT, Set.of(Stage.SPROUT, Stage.FLOWER, Stage.FRUIT), 7, "지주와 줄기 살피기",
                        "줄기를 받치는 지주와 끈을 살펴보세요. 줄기가 조이지 않게 여유를 두고 정리해 주세요.", 5, TOMATO_SOURCE));
    }
}
