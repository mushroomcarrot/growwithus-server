package community.likelion.growwithusserver.weather.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.weather.data.dto.WeatherSyncRequestDto;
import community.likelion.growwithusserver.weather.data.dto.WeatherSyncResponseDto;
import community.likelion.growwithusserver.weather.service.WeatherSyncService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// X-Service-Key 헤더 검증은 InternalServiceKeyFilter와 SecurityConfiguration이 한다.
@RestController
@RequestMapping("/internal/weather")
@RequiredArgsConstructor
public class WeatherSyncController {
    private final WeatherSyncService weatherSyncService;

    @PostMapping("/sync")
    @Operation(summary = "기상청 단기예보, 실황 수집")
    public GlobalResponse<WeatherSyncResponseDto> sync(@RequestBody(required = false) WeatherSyncRequestDto weatherSyncRequestDto) {
        List<String> farmIds = weatherSyncRequestDto == null ? null : weatherSyncRequestDto.getFarmIds();

        return GlobalResponse.ok(weatherSyncService.sync(farmIds));
    }
}
