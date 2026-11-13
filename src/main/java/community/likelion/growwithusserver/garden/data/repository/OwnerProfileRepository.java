package community.likelion.growwithusserver.garden.data.repository;

import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnerProfileRepository extends JpaRepository<OwnerProfile, Long> {
    Optional<OwnerProfile> findByUserUsername(String username);

    Optional<OwnerProfile> findByUserId(Long userId);

    List<OwnerProfile> findAllByUserIdIn(List<Long> userIds);

    boolean existsByUserId(Long userId);
}
