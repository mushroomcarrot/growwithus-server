package community.likelion.growwithusserver.user.profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import java.util.Optional;
@Configuration
public class OwnerSummaryConfiguration {
    @Bean @ConditionalOnMissingBean(OwnerSummaryReader.class)
    public OwnerSummaryReader ownerSummaryReader() { return username -> Optional.empty(); }
}
