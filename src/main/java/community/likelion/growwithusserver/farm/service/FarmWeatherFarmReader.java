package community.likelion.growwithusserver.farm.service;

import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import community.likelion.growwithusserver.weather.farm.WeatherFarm;
import community.likelion.growwithusserver.weather.farm.WeatherFarmReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 날씨 수집 배치에 농장과 격자를 준다. 이 구현체가 등록되어 설정의 기본 격자는 더 이상 쓰이지 않는다.
@Component
@RequiredArgsConstructor
public class FarmWeatherFarmReader implements WeatherFarmReader {
    private final FarmRepository farmRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WeatherFarm> findFarms(List<Long> farmIds) {
        List<Farm> farms = farmIds == null || farmIds.isEmpty()
                ? farmRepository.findAll()
                : farmRepository.findAllById(farmIds);

        return farms.stream()
                .map(farm -> new WeatherFarm(farm.getId(), farm.getNx(), farm.getNy()))
                .toList();
    }
}
