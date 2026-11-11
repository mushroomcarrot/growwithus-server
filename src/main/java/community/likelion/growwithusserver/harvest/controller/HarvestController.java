package community.likelion.growwithusserver.harvest.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.harvest.data.dto.HarvestResponseDto;
import community.likelion.growwithusserver.harvest.data.dto.OutingResponseDto;
import community.likelion.growwithusserver.harvest.data.dto.VisitSlotResponseDto;
import community.likelion.growwithusserver.harvest.service.HarvestService;
import community.likelion.growwithusserver.harvest.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/gardens/{gardenId}")
@RequiredArgsConstructor
public class HarvestController {
    private final HarvestService harvestService;
    private final VisitService visitService;

    @GetMapping("/harvest")
    @Operation(summary = "수확 안내 (AI 예측 수확일, 함께 돌본 일수)")
    public GlobalResponse<HarvestResponseDto> getHarvest(@PathVariable String gardenId) {
        return harvestService.getHarvest(SecurityUtil.getUsername(), gardenId);
    }

    @GetMapping("/visit-slots")
    @Operation(summary = "방문 가능 시간 (주말 슬롯, from과 to를 생략하면 내일부터 2주)")
    public GlobalResponse<List<VisitSlotResponseDto>> getVisitSlots(
            @PathVariable String gardenId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return visitService.getVisitSlots(SecurityUtil.getUsername(), gardenId, from, to);
    }

    @GetMapping("/outings")
    @Operation(summary = "처인 나들이 추천 (체험농장, 식당)")
    public GlobalResponse<List<OutingResponseDto>> getOutings(@PathVariable String gardenId) {
        return harvestService.getOutings(SecurityUtil.getUsername(), gardenId);
    }
}
