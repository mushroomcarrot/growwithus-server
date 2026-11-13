package community.likelion.growwithusserver.farm.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.farm.data.dto.PlotAdminRequestDto;
import community.likelion.growwithusserver.farm.data.dto.PlotAdminResponseDto;
import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.exception.CropNotFoundException;
import community.likelion.growwithusserver.farm.data.exception.FarmNotFoundException;
import community.likelion.growwithusserver.farm.data.exception.PlotAlreadyAssignedException;
import community.likelion.growwithusserver.farm.data.exception.PlotRequestInvalidException;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.farm.garden.PlotGardenLink;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.exception.UserNotFoundException;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 농장의 칸을 운영자가 넣고 고치는 내부 관리 기능 (X-Service-Key).
 *
 * 공공데이터에는 칸 정보가 없어서, 실제 농장은 운영자가 칸과 작물, 담당 관리사를 넣어야 분양받을 수 있다.
 * 1. 없는 칸은 만들고, 있는 칸은 보낸 값만 고친다.
 * 2. 이미 분양된 칸은 작물을 바꿀 수 없고, 담당 관리사는 비어 있을 때만 채울 수 있다 (C4093).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmPlotAdminService {
    // 칸 번호: 한글, 영문, 숫자로 시작하고 하이픈을 쓸 수 있다 (예: B-14). 20자 이하
    private static final Pattern PLOT_CODE = Pattern.compile("[A-Za-z0-9가-힣][A-Za-z0-9가-힣-]{0,19}");

    private final FarmRepository farmRepository;
    private final PlotRepository plotRepository;
    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final PlotGardenLink plotGardenLink;

    @Transactional(readOnly = true)
    public GlobalResponse<List<PlotAdminResponseDto>> getPlots(String farmId) {
        Long farmPk = IdPrefix.FARM.parse(farmId);
        if (!farmRepository.existsById(farmPk)) {
            log.error("[getPlots] 농장 조회 실패: {}", farmId);
            throw new FarmNotFoundException();
        }
        return GlobalResponse.ok(plotRepository.findAllByFarm(farmPk).stream()
                .map(plot -> PlotAdminResponseDto.of(farmPk, plot))
                .toList());
    }

    @Transactional
    public GlobalResponse<PlotAdminResponseDto> upsertPlot(String farmId, String plotCode, PlotAdminRequestDto request) {
        Long farmPk = IdPrefix.FARM.parse(farmId);
        Farm farm = farmRepository.findById(farmPk).orElseThrow(() -> {
            log.error("[upsertPlot] 농장 조회 실패: {}", farmId);
            return new FarmNotFoundException();
        });
        if (request == null || plotCode == null || !PLOT_CODE.matcher(plotCode).matches()) {
            log.error("[upsertPlot] 잘못된 칸 번호: {}", plotCode);
            throw new PlotRequestInvalidException();
        }

        Crop crop = request.getCropId() == null ? null : findCrop(request.getCropId());
        User caretaker = request.getCaretakerUsername() == null ? null : findCaretaker(request.getCaretakerUsername());

        Plot plot = plotRepository.findPlotForUpdate(farmPk, plotCode).orElse(null);
        if (plot == null) {
            if (crop == null) {
                log.error("[upsertPlot] 새 칸에 작물이 없음: {} ({})", farmId, plotCode);
                throw new PlotRequestInvalidException();
            }
            try {
                plot = plotRepository.saveAndFlush(Plot.builder().farm(farm).plotCode(plotCode).crop(crop).caretaker(caretaker).build());
            } catch (DataIntegrityViolationException e) {
                // 같은 칸을 동시에 만든 경우 (farm_id, plot_code 유니크)
                log.error("[upsertPlot] 칸 중복 생성: {} ({})", farmId, plotCode);
                throw new PlotAlreadyAssignedException();
            }
            log.info("[upsertPlot] 칸 생성: {} ({})", farmId, plotCode);
            return GlobalResponse.ok(PlotAdminResponseDto.of(farmPk, plot));
        }

        if (crop != null && !crop.getId().equals(plot.getCrop().getId())) {
            if (plot.isAssigned()) {
                log.error("[upsertPlot] 분양된 칸의 작물 변경 시도: {} ({})", farmId, plotCode);
                throw new PlotAlreadyAssignedException();
            }
            plot.setCrop(crop);
        }
        if (caretaker != null && (plot.getCaretaker() == null || !caretaker.getId().equals(plot.getCaretaker().getId()))) {
            if (plot.isAssigned() && plot.getCaretaker() != null) {
                log.error("[upsertPlot] 분양된 칸의 담당 관리사 변경 시도: {} ({})", farmId, plotCode);
                throw new PlotAlreadyAssignedException();
            }
            plot.setCaretaker(caretaker);
            if (plot.isAssigned()) {
                plotGardenLink.assignCaretaker(plot.getId(), caretaker);
            }
        }
        log.info("[upsertPlot] 칸 수정: {} ({})", farmId, plotCode);
        return GlobalResponse.ok(PlotAdminResponseDto.of(farmPk, plot));
    }

    private Crop findCrop(String cropId) {
        return cropRepository.findById(IdPrefix.CROP.parse(cropId)).orElseThrow(() -> {
            log.error("[findCrop] 작물 조회 실패: {}", cropId);
            return new CropNotFoundException();
        });
    }

    private User findCaretaker(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> {
            log.error("[findCaretaker] 관리사 조회 실패: {}", username);
            return new UserNotFoundException();
        });
        if (user.getRole() != UserRole.CARETAKER) {
            log.error("[findCaretaker] 관리사가 아닌 회원: {} ({})", username, user.getRole());
            throw new PlotRequestInvalidException();
        }
        return user;
    }
}
