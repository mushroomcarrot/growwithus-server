package community.likelion.growwithusserver.garden.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.garden.data.dto.HeartResponseDto;
import community.likelion.growwithusserver.garden.service.CareLogHeartService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/care-logs/{careLogId}")
@RequiredArgsConstructor
public class CareLogHeartController {
    private final CareLogHeartService careLogHeartService;

    @PostMapping("/hearts")
    @Operation(summary = "감사 하트 보내기 (돌봄 기록당 1회, 관리사 알림)")
    public GlobalResponse<HeartResponseDto> sendHeart(@PathVariable String careLogId) {
        return careLogHeartService.sendHeart(SecurityUtil.getUsername(), careLogId);
    }
}
