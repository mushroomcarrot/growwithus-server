package community.likelion.growwithusserver.weather;

import community.likelion.growwithusserver.common.id.InvalidIdException;
import community.likelion.growwithusserver.weather.client.KmaClient;
import community.likelion.growwithusserver.weather.data.dto.WeatherForecastData;
import community.likelion.growwithusserver.weather.data.dto.WeatherSyncResponseDto;
import community.likelion.growwithusserver.weather.data.exception.KmaApiException;
import community.likelion.growwithusserver.weather.farm.WeatherFarm;
import community.likelion.growwithusserver.weather.farm.WeatherFarmReader;
import community.likelion.growwithusserver.weather.service.WeatherService;
import community.likelion.growwithusserver.weather.service.WeatherSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherSyncServiceTest {
    @Mock
    private KmaClient kmaClient;
    @Mock
    private WeatherFarmReader weatherFarmReader;
    @Mock
    private WeatherService weatherService;

    private WeatherSyncService weatherSyncService;

    private final List<WeatherForecastData> forecasts = List.of(
            new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 12, 0), 10.0, 20, 0, 1, false));
    private final List<WeatherForecastData> observation = List.of(
            new WeatherForecastData(LocalDateTime.of(2026, 11, 11, 11, 0), 9.2, null, 0, null, true));

    @BeforeEach
    void setUp() {
        weatherSyncService = new WeatherSyncService(kmaClient, weatherFarmReader, weatherService);
    }

    @Test
    void 예보를_먼저_저장하고_실황을_나중에_저장한다() {
        when(kmaClient.isConfigured()).thenReturn(true);
        when(weatherFarmReader.findFarms(List.of(3L))).thenReturn(List.of(new WeatherFarm(3L, 64, 119)));
        when(kmaClient.fetchForecasts(eq(64), eq(119), any())).thenReturn(forecasts);
        when(kmaClient.fetchObservation(eq(64), eq(119), any())).thenReturn(observation);

        WeatherSyncResponseDto result = weatherSyncService.sync(List.of("farm_3"));

        assertThat(result.getSyncedFarms()).isEqualTo(1);
        assertThat(result.getFailedFarms()).isEmpty();
        InOrder inOrder = inOrder(weatherService);
        inOrder.verify(weatherService).saveForecasts(64, 119, forecasts);
        inOrder.verify(weatherService).saveForecasts(64, 119, observation);
    }

    @Test
    void 같은_격자의_농장은_한_번만_받는다() {
        when(kmaClient.isConfigured()).thenReturn(true);
        when(weatherFarmReader.findFarms(List.of())).thenReturn(List.of(
                new WeatherFarm(1L, 64, 119), new WeatherFarm(2L, 64, 119), new WeatherFarm(3L, 66, 118)));
        when(kmaClient.fetchForecasts(anyInt(), anyInt(), any())).thenReturn(forecasts);
        when(kmaClient.fetchObservation(anyInt(), anyInt(), any())).thenReturn(observation);

        WeatherSyncResponseDto result = weatherSyncService.sync(null);

        assertThat(result.getSyncedFarms()).isEqualTo(3);
        verify(kmaClient, times(1)).fetchForecasts(eq(64), eq(119), any());
        verify(kmaClient, times(1)).fetchForecasts(eq(66), eq(118), any());
    }

    @Test
    void 한_격자가_실패해도_다른_격자는_저장하고_실패한_농장을_알려준다() {
        when(kmaClient.isConfigured()).thenReturn(true);
        when(weatherFarmReader.findFarms(List.of())).thenReturn(List.of(
                new WeatherFarm(1L, 64, 119), new WeatherFarm(null, 66, 118)));
        when(kmaClient.fetchForecasts(eq(64), eq(119), any())).thenReturn(forecasts);
        when(kmaClient.fetchObservation(eq(64), eq(119), any())).thenReturn(observation);
        when(kmaClient.fetchForecasts(eq(66), eq(118), any())).thenThrow(new KmaApiException());
        when(kmaClient.fetchObservation(eq(66), eq(118), any())).thenReturn(observation);

        WeatherSyncResponseDto result = weatherSyncService.sync(List.of());

        assertThat(result.getSyncedFarms()).isEqualTo(1);
        assertThat(result.getFailedFarms()).containsExactly("grid_66_118");
        // 예보가 실패해도 받은 실황은 저장한다
        verify(weatherService).saveForecasts(66, 118, observation);
        verify(weatherService, never()).saveForecasts(66, 118, forecasts);
    }

    @Test
    void 인증키가_없으면_호출하지_않고_모두_실패로_돌려준다() {
        when(kmaClient.isConfigured()).thenReturn(false);
        when(weatherFarmReader.findFarms(List.of())).thenReturn(List.of(new WeatherFarm(null, 64, 119)));

        WeatherSyncResponseDto result = weatherSyncService.sync(List.of());

        assertThat(result.getSyncedFarms()).isZero();
        assertThat(result.getFailedFarms()).containsExactly("grid_64_119");
        verify(kmaClient, never()).fetchForecasts(anyInt(), anyInt(), any());
        verify(weatherService, never()).saveForecasts(anyInt(), anyInt(), anyList());
    }

    @Test
    void 농장_ID_형식이_틀리면_C002다() {
        assertThatThrownBy(() -> weatherSyncService.sync(List.of("gdn_3"))).isInstanceOf(InvalidIdException.class);
    }
}
