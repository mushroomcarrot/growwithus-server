package community.likelion.growwithusserver.caretaker.farm;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration
public class CaretakerFarmConfiguration {
    @Bean @ConditionalOnMissingBean(CaretakerFarmReader.class)
    CaretakerFarmReader unconfiguredCaretakerFarmReader() { return id -> java.util.Optional.empty(); }
}
