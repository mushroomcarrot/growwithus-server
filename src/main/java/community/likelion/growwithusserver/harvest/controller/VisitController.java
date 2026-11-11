package community.likelion.growwithusserver.harvest.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.harvest.data.dto.VisitRequestDto;
import community.likelion.growwithusserver.harvest.data.dto.VisitResponseDto;
import community.likelion.growwithusserver.harvest.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/visits")
@RequiredArgsConstructor
public class VisitController {
    private final VisitService visitService;

    @PostMapping
    @Operation(summary = "주말 방문 예약 (관리사 알림)")
    public GlobalResponse<VisitResponseDto> reserve(@Valid @RequestBody VisitRequestDto visitRequestDto) {
        return visitService.reserve(SecurityUtil.getUsername(), visitRequestDto);
    }

    @DeleteMapping("/{visitId}")
    @Operation(summary = "방문 예약 취소 (방문 하루 전까지)")
    public GlobalResponse<Void> cancel(@PathVariable String visitId) {
        return visitService.cancel(SecurityUtil.getUsername(), visitId);
    }
}
