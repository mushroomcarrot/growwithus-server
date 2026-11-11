package community.likelion.growwithusserver.harvest.data.repository;

import community.likelion.growwithusserver.harvest.data.entity.Outing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutingRepository extends JpaRepository<Outing, Long> {
}
