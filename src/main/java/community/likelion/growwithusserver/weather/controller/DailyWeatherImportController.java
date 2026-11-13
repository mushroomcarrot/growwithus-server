package community.likelion.growwithusserver.weather.controller;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.weather.service.DailyWeatherImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class DailyWeatherImportController {
    private final DailyWeatherImportService service;
    @PostMapping("/internal/weather/daily")
    public GlobalResponse<DailyWeatherImportService.Imported> importDays(@RequestBody DailyWeatherImportService.ImportRequest request) {
        return GlobalResponse.ok(service.importDays(request));
    }
}
