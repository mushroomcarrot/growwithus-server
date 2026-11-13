package community.likelion.growwithusserver.growth.service;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration
public class GrowthProjectionConfiguration {
    @Bean @ConditionalOnMissingBean(GrowthProjection.class)
    public GrowthProjection growthProjection() { return (id, stage, gdd, date, event) -> event; }
}
