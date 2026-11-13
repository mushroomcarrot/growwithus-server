package community.likelion.growwithusserver.farm;

import community.likelion.growwithusserver.farm.data.dto.FarmSyncResponseDto;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.farm.service.FarmSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmSyncServiceTest {

    @Mock
    private FarmRepository farmRepository;

    private FarmSyncService farmSyncService;

    @BeforeEach
    void setUp() {
        farmSyncService = new FarmSyncService(farmRepository);
        ReflectionTestUtils.setField(farmSyncService, "farmFile", new ClassPathResource("data/weekend-farms.csv"));
    }

    @Test
    void 처음_동기화하면_원본의_농장을_모두_추가한다() {
        when(farmRepository.findByExternalId(anyString())).thenReturn(Optional.empty());

        FarmSyncResponseDto response = farmSyncService.sync();

        assertThat(response.getTotalFarms()).isEqualTo(8);
        assertThat(response.getAddedFarms()).isEqualTo(8);
        assertThat(response.getUpdatedFarms()).isZero();
        assertThat(response.getSkippedFarms()).isEmpty();

        ArgumentCaptor<Farm> captor = ArgumentCaptor.forClass(Farm.class);
        verify(farmRepository, times(8)).save(captor.capture());
        Map<String, Farm> farms = new HashMap<>();
        captor.getAllValues().forEach(farm -> farms.put(farm.getName(), farm));

        Farm yangdal = farms.get("양달농원");
        assertThat(yangdal.getDistrict()).isEqualTo(District.CHEOIN);
        assertThat(yangdal.getAddress()).isEqualTo("경기도 용인시 처인구 원삼면 학일리 368-2번지 일원");
        assertThat(yangdal.getNx()).isEqualTo(66);
        assertThat(yangdal.getNy()).isEqualTo(118);
        assertThat(yangdal.getExternalId()).isEqualTo("yongin-weekend-farm:양달농원");
        // 보정동은 기흥구다
        assertThat(farms.get("삼막곡").getDistrict()).isEqualTo(District.GIHEUNG);
    }

    @Test
    void 이미_있고_값이_같으면_아무것도_바꾸지_않는다() {
        Map<String, Farm> stored = new HashMap<>();
        when(farmRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(0);
            stored.put(farm.getExternalId(), farm);
            return farm;
        });
        farmSyncService.sync();

        when(farmRepository.findByExternalId(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<String>getArgument(0))));
        FarmSyncResponseDto response = farmSyncService.sync();

        assertThat(response.getAddedFarms()).isZero();
        assertThat(response.getUpdatedFarms()).isZero();
        verify(farmRepository, times(8)).save(any(Farm.class));
    }

    @Test
    void 저장된_값이_원본과_다르면_원본으로_고친다() {
        Farm outdated = Farm.builder()
                .name("양달농원").address("예전 주소").latitude(0).longitude(0)
                .district(District.SUJI).nx(1).ny(1).externalId("yongin-weekend-farm:양달농원")
                .build();
        when(farmRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(farmRepository.findByExternalId("yongin-weekend-farm:양달농원")).thenReturn(Optional.of(outdated));

        FarmSyncResponseDto response = farmSyncService.sync();

        assertThat(response.getAddedFarms()).isEqualTo(7);
        assertThat(response.getUpdatedFarms()).isEqualTo(1);
        assertThat(outdated.getDistrict()).isEqualTo(District.CHEOIN);
        assertThat(outdated.getNx()).isEqualTo(66);
        verify(farmRepository, never()).save(outdated);
        assertThat(List.of(outdated.getAddress())).containsExactly("경기도 용인시 처인구 원삼면 학일리 368-2번지 일원");
    }
}
