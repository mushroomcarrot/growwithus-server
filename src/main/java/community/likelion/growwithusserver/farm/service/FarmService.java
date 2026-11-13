package community.likelion.growwithusserver.farm.service;

import community.likelion.growwithusserver.common.geo.GeoDistance;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretaker;
import community.likelion.growwithusserver.farm.caretaker.FarmCaretakerReader;
import community.likelion.growwithusserver.farm.data.dto.CropResponseDto;
import community.likelion.growwithusserver.farm.data.dto.FarmItemDto;
import community.likelion.growwithusserver.farm.data.dto.PlotPreviewResponseDto;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.exception.FarmNotFoundException;
import community.likelion.growwithusserver.farm.data.exception.PlotAlreadyAssignedException;
import community.likelion.growwithusserver.farm.data.exception.PlotNotFoundException;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmService {
    // 월 이용료 (가정치, api-spec.md 2장)
    public static final int MONTHLY_FEE = 30000;

    private final FarmRepository farmRepository;
    private final PlotRepository plotRepository;
    private final CropRepository cropRepository;
    private final FarmCaretakerReader farmCaretakerReader;

    /**
     * @param district 농장이 있는 구. null이면 전체
     * @param from     거리를 잴 출발 구 (사는 곳). null이면 distanceKm을 비운다
     */
    @Transactional(readOnly = true)
    public GlobalResponse<CursorPageResponse<FarmItemDto>> getFarms(District district, District from, CursorPageRequest pageRequest) {
        Long cursorId = pageRequest.getCursorId(IdPrefix.FARM);
        List<Farm> fetched = farmRepository.findPage(district, cursorId, PageRequest.of(0, pageRequest.getFetchSize()));

        CursorPageResponse<FarmItemDto> page = CursorPageResponse
                .of(fetched, pageRequest.getLimit(), farm -> IdPrefix.FARM.format(farm.getId()))
                .map(farm -> FarmItemDto.of(farm, distanceFrom(from, farm)));

        return GlobalResponse.ok(page);
    }

    @Transactional(readOnly = true)
    public GlobalResponse<PlotPreviewResponseDto> getPlotPreview(String farmId, String plotCode) {
        Long farmPk = IdPrefix.FARM.parse(farmId);
        if (!farmRepository.existsById(farmPk)) {
            log.error("[getPlotPreview] 농장 조회 실패: {}", farmId);
            throw new FarmNotFoundException();
        }

        Plot plot = plotRepository.findPlot(farmPk, plotCode)
                .orElseThrow(() -> {
                    log.error("[getPlotPreview] 칸 조회 실패: {} ({})", farmId, plotCode);
                    return new PlotNotFoundException();
                });
        if (plot.isAssigned()) {
            log.error("[getPlotPreview] 이미 분양된 칸: {} ({})", farmId, plotCode);
            throw new PlotAlreadyAssignedException();
        }

        return GlobalResponse.ok(PlotPreviewResponseDto.builder()
                .farmId(farmId)
                .plotCode(plot.getPlotCode())
                .available(true)
                .crop(PlotPreviewResponseDto.CropSummary.builder()
                        .cropId(IdPrefix.CROP.format(plot.getCrop().getId()))
                        .name(plot.getCrop().getName())
                        .characterName(plot.getCrop().getCharacterName())
                        .build())
                .caretaker(toCaretakerSummary(plot.getCaretaker()))
                .monthlyFee(MONTHLY_FEE)
                .build());
    }

    @Transactional(readOnly = true)
    public GlobalResponse<List<CropResponseDto>> getCrops() {
        return GlobalResponse.ok(cropRepository.findAllByOrderByIdAsc().stream()
                .map(CropResponseDto::from)
                .toList());
    }

    // 관리사 프로필이 있으면 그 성함과 아바타를, 없으면 회원 이름만 내려준다
    public PlotPreviewResponseDto.CaretakerSummary toCaretakerSummary(User caretaker) {
        if (caretaker == null) {
            return null;
        }

        FarmCaretaker profile = farmCaretakerReader.findByUsername(caretaker.getUsername()).orElse(null);
        return PlotPreviewResponseDto.CaretakerSummary.builder()
                .caretakerId(profile == null ? null : IdPrefix.CARETAKER.format(profile.caretakerId()))
                .name(profile == null ? caretaker.getName() : profile.name())
                .avatarUrl(profile == null ? null : profile.avatarUrl())
                .build();
    }

    private Double distanceFrom(District from, Farm farm) {
        return from == null
                ? null
                : GeoDistance.km(from.getLatitude(), from.getLongitude(), farm.getLatitude(), farm.getLongitude());
    }
}
