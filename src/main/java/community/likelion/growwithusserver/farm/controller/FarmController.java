package community.likelion.growwithusserver.farm.controller;

import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.pagination.CursorPageResponse;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.farm.data.dto.CropResponseDto;
import community.likelion.growwithusserver.farm.data.dto.FarmItemDto;
import community.likelion.growwithusserver.farm.data.dto.PlotPreviewResponseDto;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.service.FarmService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FarmController {
    private final FarmService farmService;

    @GetMapping("/farms")
    @Operation(summary = "주말농장 목록 (district로 구 필터, from을 주면 그 구에서의 거리)")
    public GlobalResponse<CursorPageResponse<FarmItemDto>> getFarms(@RequestParam(required = false) District district,
                                                                    @RequestParam(required = false) District from,
                                                                    @Valid @ModelAttribute CursorPageRequest pageRequest) {
        return farmService.getFarms(district, from, pageRequest);
    }

    @GetMapping("/farms/{farmId}/plots/{plotCode}")
    @Operation(summary = "칸 번호로 작물과 담당 관리사 미리보기")
    public GlobalResponse<PlotPreviewResponseDto> getPlotPreview(@PathVariable String farmId, @PathVariable String plotCode) {
        return farmService.getPlotPreview(farmId, plotCode);
    }

    @GetMapping("/crops")
    @Operation(summary = "작물과 생육 기준")
    public GlobalResponse<List<CropResponseDto>> getCrops() {
        return farmService.getCrops();
    }
}
