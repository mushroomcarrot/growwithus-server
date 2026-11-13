package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.demo.data.dto.DemoStateResponseDto;
import community.likelion.growwithusserver.demo.data.dto.DemoWeatherRequestDto;
import community.likelion.growwithusserver.demo.data.dto.DemoWeatherResponseDto;
import community.likelion.growwithusserver.demo.data.exception.DemoWeatherNotAllowedException;
import community.likelion.growwithusserver.weather.data.dto.CurrentWeatherDto;
import community.likelion.growwithusserver.weather.data.enums.WeatherCondition;
import community.likelion.growwithusserver.weather.service.DemoWeatherStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 시연 날씨와 실제 시연 텃밭의 작업 상태를 연결한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoService {
    private final DemoWeatherStore demoWeatherStore;
    private final DemoGardenService demoGardenService;

    public GlobalResponse<DemoStateResponseDto> getState() {
        return GlobalResponse.ok(currentState());
    }

    public GlobalResponse<DemoWeatherResponseDto> changeWeather(DemoWeatherRequestDto demoWeatherRequestDto) {
        WeatherCondition condition = demoWeatherRequestDto.getCondition();
        String animation = resolveAnimation(condition);

        demoWeatherStore.set(condition);
        var refreshed = demoGardenService.refreshTasks();
        log.info("[changeWeather] 시연 날씨 변경: {}", condition);

        return GlobalResponse.ok(DemoWeatherResponseDto.builder()
                .condition(condition)
                .animation(animation)
                .weatherNotice(refreshed.notice())
                .regeneratedTasks(refreshed.generated())
                .build());
    }

    // 맑음으로 되돌린다. 실제 날씨와 상관없이 시연이 항상 같은 상태에서 시작하도록 덮어쓰기를 지우지 않고 SUNNY로 둔다.
    public GlobalResponse<DemoStateResponseDto> reset() {
        demoWeatherStore.set(WeatherCondition.SUNNY);
        demoGardenService.refreshTasks();
        log.info("[reset] 시연 상태 초기화");

        return GlobalResponse.ok(currentState());
    }

    private DemoStateResponseDto currentState() {
        var state = DemoStateResponseDto.builder()
                .weather(demoWeatherStore.get().map(CurrentWeatherDto::getCondition).orElse(null))
                .build();
        demoGardenService.populate(state);
        return state;
    }

    // 시연에서 쓰는 날씨와 캐릭터 애니메이션. 그 외 날씨는 C002
    private String resolveAnimation(WeatherCondition condition) {
        return switch (condition) {
            case SUNNY -> "sunny_happy";
            case RAIN -> "rain_umbrella";
            case HOT -> "hot_sweat";
            case CLOUDY, COLD -> {
                log.error("[resolveAnimation] 시연에서 쓸 수 없는 날씨: {}", condition);
                throw new DemoWeatherNotAllowedException();
            }
        };
    }
}
