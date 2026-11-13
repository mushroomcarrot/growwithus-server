package community.likelion.growwithusserver.growth.service;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.time.DateTimes;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.growth.data.entity.*;
import community.likelion.growwithusserver.growth.data.repository.*;
import community.likelion.growwithusserver.growth.weather.GrowthWeatherReader;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class GrowthService {
    private final CareGardenReader gardens;
    private final GrowthWeatherReader weather;
    private final GrowthStateRepository states;
    private final GrowthEventRepository events;
    private final TaskPhotoRepository photos;
    private final TaskRepository tasks;
    private final ApplicationEventPublisher publisher;
    private final GrowthProjection projection;
    private record Updated(GrowthDtos.Detail detail, String eventId) {}

    @Transactional
    public GrowthDtos.Detail get(String username, Long id) {
        var garden = garden(id);
        if (!username.equals(garden.ownerUsername())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        return update(garden, null).detail();
    }

    @Transactional
    public GrowthDtos.Detail refreshSystem(Long id) { return update(garden(id), null).detail(); }

    @Transactional
    public GrowthDtos.Checked check(String username, Long id, GrowthDtos.CheckRequest request) {
        var garden = garden(id);
        if (!username.equals(garden.caretakerUsername())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        if (request == null || request.getObserved() == null) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        if (request.getPhotoId() != null) {
            var photo = photos.findById(IdPrefix.PHOTO.parse(request.getPhotoId())).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
            var task = tasks.findById(photo.getTaskId()).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
            if (!username.equals(photo.getCaretakerUsername()) || !id.equals(task.getGardenId())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        if (request.getObserved() == GrowthDtos.Observed.NO_CHANGE) {
            var current = states.findById(id);
            return GrowthDtos.Checked.builder().stage(current.map(GrowthState::getStage).orElse(garden.stage()))
                    .calibrated(false).calibratedAt(current.map(s -> DateTimes.toOffset(s.getCalibratedAt())).orElse(null)).build();
        }
        var result = update(garden, Stage.valueOf(request.getObserved().name()));
        return GrowthDtos.Checked.builder().stage(result.detail().getStage()).calibrated(true)
                .calibratedAt(result.detail().getCalibratedAt()).eventId(result.eventId()).build();
    }

    private CareGardenReader.Garden garden(Long id) {
        var garden = gardens.findGardenForUpdate(id).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!id.equals(garden.gardenId())) throw new VoiceMemoException(ErrorCode.DATA_NOT_FOUND);
        return garden;
    }

    // 시연 전용 상태가 있는 텃밭만 합성 기온을 사용한다. 실제 일별 기상 자료는 변경하지 않는다.
    private Updated simulate(CareGardenReader.Garden garden, GrowthState state, Stage observed) {
        LocalDate today = LocalDate.now();
        int days = state.getDemoDays();
        double dailyGdd = (garden.requiredGdd().get(Stage.FRUIT) - garden.requiredGdd().get(Stage.FLOWER)) / 18;
        var weatherDays = java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> new GrowthCalculator.WeatherDay(today.plusDays(i), garden.baseTemp() + dailyGdd))
                .toList();
        Stage start = state.getDemoStartStage() == null ? Stage.FLOWER : state.getDemoStartStage();
        var result = new GrowthCalculator().calculate(today, today.plusDays(days), garden.baseTemp(),
                garden.requiredGdd(), weatherDays, new GrowthCalculator.Calibration(today, start));
        Stage target = result.stage();
        int totalDays = (int) Math.ceil((garden.requiredGdd().get(Stage.FRUIT)
                - garden.requiredGdd().get(start)) / dailyGdd);
        if (days >= totalDays) target = Stage.FRUIT;
        if (observed != null) {
            state.setCalibrationOn(today);
            state.setCalibrationStage(observed);
            state.setCalibratedAt(LocalDateTime.now());
        }
        if (state.getCalibrationStage() != null && state.getCalibrationStage().ordinal() > target.ordinal()) {
            target = state.getCalibrationStage();
        }
        double gdd = Math.max(result.accumulatedGdd(), garden.requiredGdd().get(target));
        LocalDate harvest = today.plusDays(target == Stage.FRUIT ? 0
                : (long) Math.ceil((garden.requiredGdd().get(Stage.FRUIT) - gdd) / dailyGdd));
        Stage next = target == Stage.FRUIT ? null : Stage.values()[target.ordinal() + 1];
        state.setStage(target);
        state.setAccumulatedGdd(Math.max(result.accumulatedGdd(), garden.requiredGdd().get(target)));
        state.setProgress(next == null ? 1 : (gdd - garden.requiredGdd().get(target))
                / (garden.requiredGdd().get(next) - garden.requiredGdd().get(target)));
        state.setExpectedHarvestDate(harvest);
        state.setUpdatedAt(LocalDateTime.now());
        states.save(state);
        String eventId = projection.synchronize(garden.gardenId(), target, state.getAccumulatedGdd(), harvest, null);
        return new Updated(GrowthDtos.Detail.builder().stage(target).level(target.ordinal() + 1)
                .accumulatedGdd(state.getAccumulatedGdd()).nextStage(next)
                .nextStageGdd(next == null ? null : garden.requiredGdd().get(next))
                .progress(state.getProgress()).expectedHarvestDate(harvest)
                .calibratedAt(DateTimes.toOffset(state.getCalibratedAt()))
                .explanation("시연용 합성 기온과 가상 일수로 계산했어요. 실제 수확 예측이 아니에요.")
                .dailyGdd(result.dailyGdd()).simulated(true).build(), eventId);
    }

    private Updated update(CareGardenReader.Garden garden, Stage observed) {
        if (garden.startedOn() == null || !Double.isFinite(garden.baseTemp()) || garden.requiredGdd().isEmpty()) {
            throw new VoiceMemoException(ErrorCode.DATA_NOT_FOUND);
        }
        LocalDate today = LocalDate.now();
        if (garden.startedOn().isAfter(today)) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        GrowthState state = states.findById(garden.gardenId()).orElseGet(() -> GrowthState.builder().gardenId(garden.gardenId()).build());
        if (state.getDemoDays() != null) return simulate(garden, state, observed);
        Stage before = state.getStage() == null ? garden.stage() : state.getStage();
        var calibration = observed != null ? new GrowthCalculator.Calibration(today, observed)
                : state.getCalibrationOn() == null ? null : new GrowthCalculator.Calibration(state.getCalibrationOn(), state.getCalibrationStage());
        var days = today.equals(garden.startedOn()) ? java.util.List.<GrowthCalculator.WeatherDay>of()
                : weather.read(garden.nx(), garden.ny(), garden.startedOn(), today.minusDays(1));
        var result = new GrowthCalculator().calculate(garden.startedOn(), today, garden.baseTemp(), garden.requiredGdd(), days, calibration);
        // 누락된 날씨로 기존 성장 단계를 낮추거나 수확일을 지어내지 않는다.
        if (result.missingWeatherDays() > 0) throw new VoiceMemoException(ErrorCode.EXTERNAL_API_ERROR);
        if (observed != null) {
            state.setCalibrationOn(today); state.setCalibrationStage(observed); state.setCalibratedAt(LocalDateTime.now());
        }
        state.setStage(result.stage()); state.setAccumulatedGdd(result.accumulatedGdd()); state.setProgress(result.progress());
        state.setExpectedHarvestDate(result.expectedHarvestDate()); state.setUpdatedAt(LocalDateTime.now()); states.save(state);
        String explanation = weather.simulated() ? "시연용 가상 기온으로 계산한 단계예요. 실제 수확 예측이 아니에요."
                : "날씨의 누적 기온으로 계산한 예상 단계예요. 실제 모습은 관리사가 확인해요.";
        String eventId = null;
        if (before != null && result.stage().ordinal() > before.ordinal()) {
            GrowthEvent event = events.save(GrowthEvent.builder().gardenId(garden.gardenId()).fromStage(before).toStage(result.stage())
                    .summary("작물이 다음 단계로 자랐어요.").createdAt(LocalDateTime.now()).build());
            eventId = IdPrefix.EVENT.format(event.getId());
            publisher.publishEvent(new GrowthChanged(garden.ownerUsername(), event.getSummary(), eventId));
        }
        eventId = projection.synchronize(garden.gardenId(), result.stage(), result.accumulatedGdd(), result.expectedHarvestDate(), eventId);
        return new Updated(GrowthDtos.Detail.builder().stage(result.stage()).level(result.level()).accumulatedGdd(result.accumulatedGdd())
                .nextStage(result.nextStage()).nextStageGdd(result.nextStageGdd()).progress(result.progress())
                .expectedHarvestDate(result.expectedHarvestDate()).calibratedAt(DateTimes.toOffset(state.getCalibratedAt()))
                .explanation(explanation).dailyGdd(result.dailyGdd()).build(), eventId);
    }
}
