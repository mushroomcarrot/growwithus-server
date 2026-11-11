package community.likelion.growwithusserver.weather.farm;

import java.util.List;

// 날씨 수집이 농장 도메인에서 필요로 하는 값. 농장 도메인이 구현체를 @Component로 등록한다.
public interface WeatherFarmReader {

    // 수집할 농장 목록. farmIds가 비어 있으면 등록된 모든 농장을 준다.
    List<WeatherFarm> findFarms(List<Long> farmIds);
}
