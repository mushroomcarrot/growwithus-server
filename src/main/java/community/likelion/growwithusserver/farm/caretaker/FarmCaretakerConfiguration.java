package community.likelion.growwithusserver.farm.caretaker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

// 관리사 프로필 도메인의 구현체가 등록되기 전까지 쓰는 기본 구현. 프로필을 찾지 못하므로
// 화면에는 회원 이름만 나가고 caretakerId와 avatarUrl은 null이다.
@Configuration
public class FarmCaretakerConfiguration {

    @Bean
    @ConditionalOnMissingBean(FarmCaretakerReader.class)
    public FarmCaretakerReader unconfiguredFarmCaretakerReader() {
        return username -> Optional.empty();
    }
}
