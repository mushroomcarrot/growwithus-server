package community.likelion.growwithusserver.weather.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.weather.client.KmaClient;
import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import community.likelion.growwithusserver.weather.data.dto.WeatherSyncResponseDto;
import community.likelion.growwithusserver.weather.farm.WeatherFarm;
import community.likelion.growwithusserver.weather.farm.WeatherFarmReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 기상청 날씨 수집 배치. 스케줄러와 POST /internal/weather/sync가 함께 쓴다.
 *
 * 한 격자의 실패가 다른 격자의 저장을 되돌리지 않도록 전체를 하나의 트랜잭션으로 묶지 않는다.
 * 저장은 격자마다 WeatherService.saveForecasts의 트랜잭션에서 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherSyncService {
    private final KmaClient kmaClient;
    private final WeatherFarmReader weatherFarmReader;
    private final WeatherService weatherService;

    /**
     * @param farmIds 수집할 농장의 외부 ID. 비어 있으면 등록된 모든 농장
     */
    public WeatherSyncResponseDto sync(List<String> farmIds) {
        List<Long> ids = farmIds == null ? List.of() : farmIds.stream().map(IdPrefix.FARM::parse).toList();
        List<WeatherFarm> farms = weatherFarmReader.findFarms(ids);

        if (!kmaClient.isConfigured()) {
            log.warn("[sync] 기상청 인증키가 없어 날씨 수집을 건너뜀: {}곳", farms.size());
            return WeatherSyncResponseDto.builder()
                    .syncedFarms(0)
                    .failedFarms(farms.stream().map(WeatherFarm::label).toList())
                    .build();
        }

        // 여러 농장이 같은 격자에 있으면 한 번만 받는다
        LocalDateTime now = LocalDateTime.now();
        Map<String, Boolean> syncedByGrid = new HashMap<>();
        int syncedFarms = 0;
        List<String> failedFarms = new ArrayList<>();
        for (WeatherFarm farm : farms) {
            boolean synced = syncedByGrid.computeIfAbsent(farm.nx() + ":" + farm.ny(), key -> syncGrid(farm.nx(), farm.ny(), now));
            if (synced) {
                syncedFarms++;
            } else {
                failedFarms.add(farm.label());
            }
        }
        log.info("[sync] 날씨 수집 완료: 성공 {}곳, 실패 {}", syncedFarms, failedFarms);

        return WeatherSyncResponseDto.builder().syncedFarms(syncedFarms).failedFarms(failedFarms).build();
    }

    // 예보를 먼저 저장하고 실황을 나중에 저장한다. 실황에 없는 강수확률과 하늘상태가 예보 값으로 남는다.
    // 하나가 실패해도 다른 하나는 저장하고, 실패한 쪽은 앞서 저장된 값이 그대로 남는다.
    private boolean syncGrid(int nx, int ny, LocalDateTime now) {
        boolean forecastSaved = fetchAndSave("단기예보", nx, ny, () -> kmaClient.fetchForecasts(nx, ny, now));
        boolean observationSaved = fetchAndSave("초단기실황", nx, ny, () -> kmaClient.fetchObservation(nx, ny, now));

        return forecastSaved && observationSaved;
    }

    private boolean fetchAndSave(String name, int nx, int ny, Supplier<List<WeatherForecastData>> fetcher) {
        try {
            List<WeatherForecastData> fetched = fetcher.get();
            if (fetched.isEmpty()) {
                log.warn("[fetchAndSave] {} 응답에 쓸 값이 없음: 격자 ({}, {})", name, nx, ny);
                return false;
            }
            weatherService.saveForecasts(nx, ny, fetched);

            return true;
        } catch (Exception e) {
            log.error("[fetchAndSave] {} 수집 실패, 앞서 저장된 값을 유지: 격자 ({}, {}), {}", name, nx, ny, e.getClass().getSimpleName());
            return false;
        }
    }
}
