package community.likelion.growwithusserver.demo;

import community.likelion.growwithusserver.demo.service.DemoVisitSeeder;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.farm.data.entity.*;
import community.likelion.growwithusserver.harvest.data.repository.VisitRepository;
import community.likelion.growwithusserver.harvest.data.dto.*;
import community.likelion.growwithusserver.harvest.data.entity.Visit;
import community.likelion.growwithusserver.harvest.service.VisitService;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemoVisitSeederTest {
    private final GardenRepository gardens = mock(GardenRepository.class);
    private final VisitRepository visits = mock(VisitRepository.class);
    private final VisitService service = mock(VisitService.class);
    private final DemoVisitSeeder seeder = new DemoVisitSeeder(gardens, visits, service);

    private void garden(String externalId) {
        when(gardens.findIdByOwnerUsername("minji")).thenReturn(Optional.of(1L));
        when(gardens.findDetail(1L)).thenReturn(Optional.of(Garden.builder().id(1L)
                .plot(Plot.builder().plotCode("B-14").farm(Farm.builder().id(2L).externalId(externalId).build()).build()).build()));
    }

    @Test void 일반_농장에는_예약하지_않는다() {
        garden("real");
        seeder.run(null);
        verifyNoInteractions(visits, service);
    }

    @Test void 미래_예약이_있으면_중복_예약하지_않는다() {
        garden("demo:theme");
        when(visits.findAllByFarmIdAndVisitDateBetweenAndStatus(eq(2L), any(), any(), any()))
                .thenReturn(List.of(Visit.builder().gardenId(1L).build()));
        seeder.run(null);
        verifyNoInteractions(service);
    }

    @Test void 공통_예약_서비스의_가능한_슬롯만_사용한다() {
        garden("demo:theme");
        when(service.getVisitSlots(eq("minji"), eq("gdn_1"), any(), any())).thenReturn(GlobalResponse.ok(List.of(
                VisitSlotResponseDto.builder().slotId("full").available(false).build(),
                VisitSlotResponseDto.builder().slotId("open").available(true).build())));
        seeder.run(null);
        verify(service).reserve(eq("minji"), argThat(request ->
                request.getGardenId().equals("gdn_1") && request.getSlotId().equals("open") && request.getHeadcount() == null));
    }
}
