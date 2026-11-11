package community.likelion.growwithusserver.weather.farm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

// 농장 도메인의 구현체가 등록되기 전까지 쓰는 기본 구현. 요청한 농장과 상관없이 설정의 기본 격자를 준다.
@Configuration
public class WeatherFarmConfiguration {

    @Bean
    @ConditionalOnMissingBean(WeatherFarmReader.class)
    public WeatherFarmReader defaultGridWeatherFarmReader(@Value("${weather.sync.default-grids}") String defaultGrids) {
        List<WeatherFarm> farms = parseGrids(defaultGrids);

        return farmIds -> farms;
    }

    // "64:119,66:118" 형식. 형식이 틀리면 부팅할 때 바로 알 수 있게 예외를 던진다.
    static List<WeatherFarm> parseGrids(String grids) {
        List<WeatherFarm> farms = new ArrayList<>();
        if (grids == null || grids.isBlank()) {
            return farms;
        }

        for (String grid : grids.split(",")) {
            String[] parts = grid.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("weather.sync.default-grids 형식 오류: " + grid);
            }
            farms.add(new WeatherFarm(null, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
        }

        return farms;
    }
}
