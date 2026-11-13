package community.likelion.growwithusserver.demo;

import community.likelion.growwithusserver.demo.service.DemoSimulationService;
import community.likelion.growwithusserver.evolution.data.repository.EvolutionEventRepository;
import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import community.likelion.growwithusserver.farm.data.entity.*;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.farm.service.FarmSeeder;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.growth.data.entity.GrowthState;
import community.likelion.growwithusserver.growth.data.repository.GrowthStateRepository;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.growth.service.GrowthService;
import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoSimulationServiceTest {
    private final GardenRepository gardens = mock(GardenRepository.class);
    private final GrowthStateRepository states = mock(GrowthStateRepository.class);
    private final GrowthService growth = mock(GrowthService.class);
    private final EvolutionEventRepository events = mock(EvolutionEventRepository.class);
    private final EvolutionEventService evolution = mock(EvolutionEventService.class);
    private final DemoSimulationService service = new DemoSimulationService(gardens, states, growth, events, evolution);

    private void garden(String externalId) {
        when(gardens.findIdByOwnerUsername("minji")).thenReturn(Optional.of(1L));
        when(gardens.findByIdForUpdate(1L)).thenReturn(Optional.of(Garden.builder().id(1L)
                .stage(GrowthStage.FLOWER).plot(Plot.builder().plotCode("B-14")
                        .farm(Farm.builder().externalId(externalId).build()).build()).build()));
    }

    @Test void 일반_농장은_변경하지_않는다() {
        garden("real");
        assertThatThrownBy(() -> service.advance(18)).isInstanceOf(VoiceMemoException.class);
        verifyNoInteractions(states, growth, evolution);
    }

    @Test void 일수_범위를_제한한다() {
        for (int days : new int[]{0, -1, 366}) {
            assertThatThrownBy(() -> service.advance(days)).isInstanceOf(VoiceMemoException.class);
        }
        verifyNoInteractions(gardens, states, growth);
    }

    @Test void 저장한_누적_일수로_공통_성장_서비스를_호출한다() {
        garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID);
        var state = GrowthState.builder().gardenId(1L).stage(Stage.FLOWER).demoDays(9).build();
        when(states.findById(1L)).thenReturn(Optional.of(state));
        when(growth.refreshSystem(1L)).thenReturn(GrowthDtos.Detail.builder()
                .stage(Stage.FRUIT).expectedHarvestDate(LocalDate.now()).build());
        assertThat(service.advance(9).getStage()).isEqualTo("FRUIT");
        assertThat(state.getDemoDays()).isEqualTo(18);
        var order = inOrder(gardens, states, growth);
        order.verify(gardens).findIdByOwnerUsername("minji");
        order.verify(gardens).findByIdForUpdate(1L);
        order.verify(states).findById(1L);
        order.verify(states).save(state);
        order.verify(growth).refreshSystem(1L);
    }

    @Test void 초기화는_관찰_보정과_가상_일수를_지운다() {
        garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID);
        var state = GrowthState.builder().gardenId(1L).stage(Stage.FRUIT)
                .demoDays(18).calibrationStage(Stage.FRUIT).calibrationOn(LocalDate.now()).build();
        when(states.findById(1L)).thenReturn(Optional.of(state));
        service.resetGrowth();
        assertThat(state.getDemoDays()).isZero();
        assertThat(state.getCalibrationStage()).isNull();
        verify(evolution).resetDemoStage(1L);
        verify(growth).refreshSystem(1L);
    }

    @Test void 시연_단계_하향은_전용_초기화로_처리한다() {
        garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID);
        var state = GrowthState.builder().gardenId(1L).stage(Stage.FLOWER).demoDays(12)
                .calibrationStage(Stage.FRUIT).build();
        when(states.findById(1L)).thenReturn(Optional.of(state));
        when(growth.refreshSystem(1L)).thenReturn(GrowthDtos.Detail.builder().stage(Stage.SEED).build());
        assertThat(service.setStage(GrowthStage.SEED).getEventId()).isNull();
        assertThat(state.getDemoDays()).isZero();
        assertThat(state.getDemoStartStage()).isEqualTo(Stage.SEED);
        assertThat(state.getCalibrationStage()).isNull();
        verify(evolution).resetDemoStage(1L, GrowthStage.SEED);
        verify(evolution, never()).advanceStage(any(), any());
    }

    @Test void 시연_단계_상승은_공통_이벤트를_반환한다() {
        garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID);
        when(evolution.advanceStage(1L, GrowthStage.FRUIT)).thenReturn(Optional.of(
                community.likelion.growwithusserver.evolution.data.entity.EvolutionEvent.builder().id(8L).build()));
        when(growth.refreshSystem(1L)).thenReturn(GrowthDtos.Detail.builder().stage(Stage.FRUIT).build());
        assertThat(service.setStage(GrowthStage.FRUIT).getEventId()).isEqualTo("evt_8");
        verify(evolution, never()).resetDemoStage(any(), any());
    }

    @Test void 같은_단계_선택은_이벤트를_추가하지_않는다() {
        garden(FarmSeeder.DEMO_FARM_EXTERNAL_ID);
        when(growth.refreshSystem(1L)).thenReturn(GrowthDtos.Detail.builder().stage(Stage.FLOWER).build());
        assertThat(service.setStage(GrowthStage.FLOWER).getEventId()).isNull();
        verifyNoInteractions(evolution, events);
    }

    @Test void 단계_선택도_일반_농장과_빈_입력을_거부한다() {
        assertThatThrownBy(() -> service.setStage(null)).isInstanceOf(VoiceMemoException.class);
        garden("real");
        assertThatThrownBy(() -> service.setStage(GrowthStage.SEED)).isInstanceOf(VoiceMemoException.class);
        verifyNoInteractions(states, growth, evolution);
    }

}
