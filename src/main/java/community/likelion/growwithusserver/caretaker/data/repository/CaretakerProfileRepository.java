package community.likelion.growwithusserver.caretaker.data.repository;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CaretakerProfileRepository extends JpaRepository<CaretakerProfile, Long> {
    Optional<CaretakerProfile> findByUsername(String username);
    boolean existsByUsername(String username);
}
