package community.likelion.growwithusserver.demo.controller;

import community.likelion.growwithusserver.demo.service.DemoResetService;
import community.likelion.growwithusserver.demo.service.DemoSimulationService;
import community.likelion.growwithusserver.demo.data.dto.DemoFastForwardRequestDto;
import community.likelion.growwithusserver.demo.data.dto.DemoFastForwardResponseDto;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.demo.data.dto.DemoStateResponseDto;
import community.likelion.growwithusserver.demo.data.dto.DemoWeatherRequestDto;
import community.likelion.growwithusserver.demo.data.dto.DemoWeatherResponseDto;
import community.likelion.growwithusserver.demo.service.DemoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ADMIN 토큰과 X-Demo-Mode 헤더 검증은 SecurityConfiguration과 DemoModeFilter가 한다.
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {
    private final DemoService demoService;
    private final DemoResetService resetService;
    private final DemoSimulationService simulation;

    @PostMapping("/fast-forward")
    @Operation(summary = "시연 시간 빨리 감기")
    public GlobalResponse<DemoFastForwardResponseDto> fastForward(
            @Valid @RequestBody DemoFastForwardRequestDto request) {
        return GlobalResponse.ok(simulation.advance(request.getDays()));
    }


    @GetMapping("/state")
    @Operation(summary = "데모 상태")
    public GlobalResponse<DemoStateResponseDto> getState() {
        return demoService.getState();
    }

    @PutMapping("/weather")
    @Operation(summary = "날씨 바꾸기 (SUNNY, RAIN, HOT)")
    public GlobalResponse<DemoWeatherResponseDto> changeWeather(@Valid @RequestBody DemoWeatherRequestDto demoWeatherRequestDto) {
        return demoService.changeWeather(demoWeatherRequestDto);
    }

    @PostMapping("/reset")
    @Operation(summary = "시연 성장과 맑음 상태로 초기화")
    public GlobalResponse<DemoStateResponseDto> reset() {
        return resetService.reset();
    }
}
