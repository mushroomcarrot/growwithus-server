package community.likelion.growwithusserver.farm.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.farm.data.dto.FarmSyncResponseDto;
import community.likelion.growwithusserver.farm.service.FarmSyncService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// X-Service-Key 헤더 검증은 InternalServiceKeyFilter와 SecurityConfiguration이 한다.
@RestController
@RequestMapping("/internal/farms")
@RequiredArgsConstructor
public class FarmSyncController {
    private final FarmSyncService farmSyncService;

    @PostMapping("/sync")
    @Operation(summary = "공공데이터 주말농장 동기화")
    public GlobalResponse<FarmSyncResponseDto> sync() {
        return GlobalResponse.ok(farmSyncService.sync());
    }
}
