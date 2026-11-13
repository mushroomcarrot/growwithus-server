package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.service.GardenDemoSeeder;
import community.likelion.growwithusserver.harvest.data.dto.VisitRequestDto;
import community.likelion.growwithusserver.harvest.data.enums.VisitStatus;
import community.likelion.growwithusserver.harvest.data.repository.VisitRepository;
import community.likelion.growwithusserver.harvest.service.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

@Component
@Order(FarmSeeder.ORDER + 20)
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"demo.enabled", "demo.seed-visit-enabled"}, havingValue = "true")
public class DemoVisitSeeder implements ApplicationRunner {
    private final GardenRepository gardens;
    private final VisitRepository visits;
    private final VisitService visitService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var id = gardens.findIdByOwnerUsername(GardenDemoSeeder.OWNER_USERNAME);
        if (id.isEmpty()) return;
        var garden = gardens.findDetail(id.get()).orElseThrow();
        if (!FarmSeeder.DEMO_FARM_EXTERNAL_ID.equals(garden.getPlot().getFarm().getExternalId())
                || !FarmSeeder.DEMO_PLOT_CODE.equals(garden.getPlot().getPlotCode())) return;
        LocalDate date = LocalDate.now().plusDays(18).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        // 날짜가 바뀌어 재부팅해도 아직 유효한 시연 예약이 있으면 추가하지 않는다.
        boolean reserved = visits.findAllByFarmIdAndVisitDateBetweenAndStatus(
                        garden.getPlot().getFarm().getId(), LocalDate.now(), LocalDate.now().plusDays(31), VisitStatus.RESERVED)
                .stream().anyMatch(v -> garden.getId().equals(v.getGardenId()));
        if (reserved) return;
        String gardenId = IdPrefix.GARDEN.format(garden.getId());
        visitService.getVisitSlots(GardenDemoSeeder.OWNER_USERNAME, gardenId, date, date).getData().stream()
                .filter(slot -> slot.isAvailable()).findFirst().ifPresent(slot ->
                        visitService.reserve(GardenDemoSeeder.OWNER_USERNAME,
                                VisitRequestDto.builder().gardenId(gardenId).slotId(slot.getSlotId())
                                        .memo("시연용 가족 방문").build()));
    }
}
