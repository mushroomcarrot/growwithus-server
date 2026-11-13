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

    private Updated update(CareGardenReader.Garden garden, Stage observed) {
        if (garden.startedOn() == null || !Double.isFinite(garden.baseTemp()) || garden.requiredGdd().isEmpty()) {
            throw new VoiceMemoException(ErrorCode.DATA_NOT_FOUND);
        }
        LocalDate today = LocalDate.now();
        if (garden.startedOn().isAfter(today)) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        GrowthState state = states.findById(garden.gardenId()).orElseGet(() -> GrowthState.builder().gardenId(garden.gardenId()).build());
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
