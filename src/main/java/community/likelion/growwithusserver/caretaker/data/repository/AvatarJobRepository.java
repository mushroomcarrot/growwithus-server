package community.likelion.growwithusserver.caretaker.data.repository;

import community.likelion.growwithusserver.caretaker.data.entity.AvatarJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AvatarJobRepository extends JpaRepository<AvatarJob, Long> {
    // 가장 최근 요청
    Optional<AvatarJob> findFirstByUsernameOrderByIdDesc(String username);
}
