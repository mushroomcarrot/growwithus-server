package community.likelion.growwithusserver.farm.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.farm.data.dto.PlotAdminRequestDto;
import community.likelion.growwithusserver.farm.data.dto.PlotAdminResponseDto;
import community.likelion.growwithusserver.farm.service.FarmPlotAdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// X-Service-Key 헤더 검증은 InternalServiceKeyFilter와 SecurityConfiguration이 한다.
@RestController
@RequestMapping("/internal/farms/{farmId}/plots")
@RequiredArgsConstructor
public class FarmPlotAdminController {
    private final FarmPlotAdminService farmPlotAdminService;

    @GetMapping
    @Operation(summary = "농장의 칸 목록 (운영자용)")
    public GlobalResponse<List<PlotAdminResponseDto>> getPlots(@PathVariable String farmId) {
        return farmPlotAdminService.getPlots(farmId);
    }

    @PutMapping("/{plotCode}")
    @Operation(summary = "칸 등록 및 수정 (작물, 담당 관리사)")
    public GlobalResponse<PlotAdminResponseDto> upsertPlot(@PathVariable String farmId, @PathVariable String plotCode,
                                                           @Valid @RequestBody PlotAdminRequestDto plotAdminRequestDto) {
        return farmPlotAdminService.upsertPlot(farmId, plotCode, plotAdminRequestDto);
    }
}
