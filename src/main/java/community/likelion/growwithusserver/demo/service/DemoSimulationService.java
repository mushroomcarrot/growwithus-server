package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import java.time.LocalDateTime;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.demo.data.dto.DemoFastForwardResponseDto;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.service.GardenDemoSeeder;
import community.likelion.growwithusserver.growth.data.entity.GrowthState;
import community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository;
import community.likelion.growwithusserver.growth.service.GrowthService;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemoSimulationService {
    private final GardenRepository gardens;
    private final GrowthStateRepository states;
    private final GrowthService growth;
    private final EvolutionEventRepository events;
    private final EvolutionEventService evolution;

    @Transactional
    public void resetGrowth() {
        Garden garden = lockedDemo();
        evolution.resetDemoStage(garden.getId());
        // 재실행 전에 남아 있던 연출은 닫는다. 이미 지급된 보상과 기록은 보존한다.
        for (var event : events.findAllByGardenIdAndAckedFalseOrderByIdAsc(garden.getId())) {
            event.setAcked(true);
            event.setAckedAt(LocalDateTime.now());
        }
        var state = states.findById(garden.getId()).orElseGet(() ->
                GrowthState.builder().gardenId(garden.getId()).build());
        state.setDemoDays(0);
        state.setDemoStartStage(Stage.FLOWER);
        state.setStage(Stage.FLOWER);
        state.setCalibrationStage(null);
        state.setCalibrationOn(null);
        state.setCalibratedAt(null);
        state.setUpdatedAt(LocalDateTime.now());
        states.save(state);
        growth.refreshSystem(garden.getId());
    }


    @Transactional
    public DemoFastForwardResponseDto advance(int days) {
        if (days < 1 || days > 365) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        Garden garden = lockedDemo();
        var state = states.findById(garden.getId()).orElseGet(() ->
                GrowthState.builder().gardenId(garden.getId()).build());
        if (state.getDemoDays() == null && garden.getStage() != GrowthStage.FLOWER) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        int elapsed = state.getDemoDays() == null ? 0 : state.getDemoDays();
        if (elapsed + days > 3650) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        state.setDemoDays(elapsed + days);
        // 저장 전에 필수 값을 채운다. refreshSystem이 계산 결과로 덮어쓴다.
        if (state.getStage() == null) state.setStage(Stage.FLOWER);
        state.setUpdatedAt(LocalDateTime.now());
        states.save(state);
        var detail = growth.refreshSystem(garden.getId());
        String eventId = events.findAllByGardenIdAndAckedFalseOrderByIdAsc(garden.getId()).stream()
                .filter(e -> e.getToStage().name().equals(detail.getStage().name()))
                .reduce((first, last) -> last).map(e -> IdPrefix.EVENT.format(e.getId())).orElse(null);
        return new DemoFastForwardResponseDto(detail.getStage().name(), eventId, detail.getExpectedHarvestDate());
    }

    @Transactional
    public DemoFastForwardResponseDto setStage(GrowthStage target) {
        if (target == null) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        Garden garden = lockedDemo();
        String eventId = null;
        if (target != garden.getStage()) {
            for (var event : events.findAllByGardenIdAndAckedFalseOrderByIdAsc(garden.getId())) {
                event.setAcked(true);
                event.setAckedAt(LocalDateTime.now());
            }
            if (target.getLevel() > garden.getStage().getLevel()) {
                eventId = evolution.advanceStage(garden.getId(), target)
                        .map(event -> IdPrefix.EVENT.format(event.getId())).orElse(null);
            } else {
                evolution.resetDemoStage(garden.getId(), target);
            }
        }
        var state = states.findById(garden.getId()).orElseGet(() ->
                GrowthState.builder().gardenId(garden.getId()).build());
        state.setDemoDays(0);
        state.setDemoStartStage(Stage.valueOf(target.name()));
        state.setStage(Stage.valueOf(target.name()));
        state.setCalibrationStage(null);
        state.setCalibrationOn(null);
        state.setCalibratedAt(null);
        state.setUpdatedAt(LocalDateTime.now());
        states.save(state);
        var detail = growth.refreshSystem(garden.getId());
        return new DemoFastForwardResponseDto(detail.getStage().name(), eventId, detail.getExpectedHarvestDate());
    }

    private Garden lockedDemo() {
        Long id = gardens.findIdByOwnerUsername(GardenDemoSeeder.OWNER_USERNAME)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        Garden garden = gardens.findByIdForUpdate(id)
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!FarmSeeder.DEMO_FARM_EXTERNAL_ID.equals(garden.getPlot().getFarm().getExternalId())
                || !FarmSeeder.DEMO_PLOT_CODE.equals(garden.getPlot().getPlotCode())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        return garden;
    }
}
