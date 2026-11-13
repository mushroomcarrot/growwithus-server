package community.likelion.growwithusserver.farm.service;

import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.repository.CropRepository;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 농장, 작물 기준 데이터 시드. 부팅할 때 없는 것만 넣는다.
 *
 * 1. 작물: 방울토마토. 기준온도 10℃와 단계별 적산온도는 명세(api-detail.md 3.3)의 예시 값이다.
 * 2. 주말농장: 비어 있으면 공공데이터 파일을 한 번 동기화한다.
 * 3. 시연 농장과 칸: demo.enabled=true일 때만 넣는다. 실제 농장이 아니라 시연용으로 만든 농장이다.
 */
@Slf4j
@Component
@Order(FarmSeeder.ORDER)
@RequiredArgsConstructor
public class FarmSeeder implements ApplicationRunner {
    public static final int ORDER = 10;
    public static final String CHERRY_TOMATO = "방울토마토";
    public static final String DEMO_FARM_EXTERNAL_ID = "demo:theme";
    public static final String DEMO_PLOT_CODE = "B-14";
    private static final List<String> DEMO_PLOT_CODES = List.of("B-13", DEMO_PLOT_CODE, "B-15");

    private final CropRepository cropRepository;
    private final FarmRepository farmRepository;
    private final PlotRepository plotRepository;
    private final FarmSyncService farmSyncService;

    @Value("${demo.enabled:false}")
    private boolean demoEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Crop cherryTomato = seedCherryTomato();

        if (farmRepository.count() == 0) {
            farmSyncService.sync();
        }

        if (demoEnabled) {
            seedDemoFarm(cherryTomato);
        }
    }

    private Crop seedCherryTomato() {
        return cropRepository.findByName(CHERRY_TOMATO).orElseGet(() -> {
            log.info("[seedCherryTomato] 작물 시드 데이터 적재: {}", CHERRY_TOMATO);
            return cropRepository.save(Crop.builder()
                    .name(CHERRY_TOMATO)
                    .characterName("토순이")
                    .baseTemp(10.0)
                    .sproutGdd(150)
                    .flowerGdd(550)
                    .fruitGdd(1000)
                    .schedule("모종을 심고 두 달쯤 지나면 열매를 딸 수 있어요")
                    .source("명세의 예시 값. 농사로 작물 생육 정보로 바꿔야 한다")
                    .build());
        });
    }

    private void seedDemoFarm(Crop crop) {
        Farm farm = farmRepository.findByExternalId(DEMO_FARM_EXTERNAL_ID).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            double latitude = 37.2342;
            double longitude = 127.2017;
            int[] grid = KmaGrid.toGrid(latitude, longitude);

            log.info("[seedDemoFarm] 시연 농장 시드 데이터 적재");
            return farmRepository.save(Farm.builder()
                    .name("처인 테마 주말농장")
                    .address("경기도 용인시 처인구 (시연용 농장)")
                    .latitude(latitude)
                    .longitude(longitude)
                    .district(District.CHEOIN)
                    .nx(grid[0])
                    .ny(grid[1])
                    .externalId(DEMO_FARM_EXTERNAL_ID)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });

        for (String plotCode : DEMO_PLOT_CODES) {
            if (!plotRepository.existsByFarmIdAndPlotCode(farm.getId(), plotCode)) {
                plotRepository.save(Plot.builder().farm(farm).plotCode(plotCode).crop(crop).build());
            }
        }
    }
}
