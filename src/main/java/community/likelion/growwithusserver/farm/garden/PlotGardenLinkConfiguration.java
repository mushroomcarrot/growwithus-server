package community.likelion.growwithusserver.farm.garden;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 텃밭 도메인의 구현체가 없을 때 쓰는 기본 구현. 칸만 고치고 텃밭은 건드리지 않는다.
@Configuration
public class PlotGardenLinkConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlotGardenLink.class)
    public PlotGardenLink unconfiguredPlotGardenLink() {
        return (plotId, caretaker) -> { };
    }
}
