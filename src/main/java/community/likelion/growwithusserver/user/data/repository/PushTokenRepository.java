package community.likelion.growwithusserver.user.data.repository;

import community.likelion.growwithusserver.user.data.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    Optional<PushToken> findByToken(String token);
}
