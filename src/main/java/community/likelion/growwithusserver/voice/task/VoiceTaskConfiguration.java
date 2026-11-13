package community.likelion.growwithusserver.voice.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class VoiceTaskConfiguration {
    @Bean
    @ConditionalOnMissingBean(VoiceTaskReader.class)
    public VoiceTaskReader unconfiguredVoiceTaskReader() {
        return new VoiceTaskReader() {
            @Override
            public Optional<VoiceTask> findTask(Long taskId) { return Optional.empty(); }

            @Override
            public Optional<VoiceTask> findTaskForUpdate(Long taskId) { return Optional.empty(); }
        };
    }
}
