package community.likelion.growwithusserver.demo.local;

import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.caretaker.farm.CaretakerFarmReader;
import community.likelion.growwithusserver.growth.weather.GrowthWeatherReader;
import community.likelion.growwithusserver.harvest.garden.*;
import community.likelion.growwithusserver.user.profile.OwnerSummaryReader;
import community.likelion.growwithusserver.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.LocalDate;
import java.util.*;

@Configuration @Profile("local-demo")
public class LocalDemoConfiguration {
    @Bean public LocalDemoData localDemoData(@Value("${local-demo.started-on}") LocalDate startedOn) { return new LocalDemoData(startedOn); }
    @Bean @Primary public CareGardenReader localDemoGardens(LocalDemoData data, UserService users) {
        return new CareGardenReader() {
            public List<Garden> findAssigned(String username) { return LocalDemoData.CARETAKER.equals(username) ? List.of(data.garden()) : List.of(); }
            public List<Garden> findAll() { return List.of(data.garden()); }
            public Optional<Garden> findGarden(Long id) { return Long.valueOf(1).equals(id) ? Optional.of(data.garden()) : Optional.empty(); }
            public Optional<Garden> findGardenForUpdate(Long id) {
                if (!Long.valueOf(1).equals(id)) return Optional.empty();
                // 시연 텃밭 하나의 변경을 실제 소유자 DB 행 잠금으로 직렬화한다.
                users.getUserForUpdate(LocalDemoData.OWNER);
                return findGarden(id);
            }
        };
    }
    @Bean @Primary public CaretakerFarmReader localDemoFarms() {
        return id -> Long.valueOf(1).equals(id) ? Optional.of(new CaretakerFarmReader.Farm(1L, "로컬 시연 농장")) : Optional.empty();
    }
    @Bean @Primary public OwnerSummaryReader localDemoOwner() {
        return username -> LocalDemoData.OWNER.equals(username)
                ? Optional.of(new OwnerSummaryReader.Summary("민지 가족", "GIHEUNG", 1L)) : Optional.empty();
    }
    @Bean @Primary public GrowthWeatherReader localDemoGrowthWeather(LocalDemoData data) {
        return new GrowthWeatherReader() {
            public List<community.likelion.growwithusserver.ai.logic.GrowthCalculator.WeatherDay> read(int nx, int ny, LocalDate from, LocalDate to) {
                return data.weather(from, to);
            }
            public boolean simulated() { return true; }
        };
    }
    @Bean @Primary public HarvestGardenReader localDemoHarvest(LocalDemoData data,
            community.likelion.growwithusserver.growth.service.GrowthStatusService growth,
            community.likelion.growwithusserver.care.data.repository.CareLogRepository logs) {
        return id -> Long.valueOf(1).equals(id) ? Optional.of(HarvestGarden.builder().gardenId(1L)
                .ownerUsername(LocalDemoData.OWNER).ownerNickname("민지 가족").characterName("토순이").farmId(1L)
                .caretakerUsername(LocalDemoData.CARETAKER).caretakerName("순자 할머니").startedOn(data.garden().startedOn())
                .stage(community.likelion.growwithusserver.farm.data.enums.GrowthStage.valueOf(growth.stage(data.garden()).name()))
                .harvestable(false).careLogCount((int) Math.min(Integer.MAX_VALUE, logs.countByGardenId(1L))).build()) : Optional.empty();
    }
}
