package community.likelion.growwithusserver.growth.data.repository;
import community.likelion.growwithusserver.growth.data.entity.GrowthEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GrowthEventRepository extends JpaRepository<GrowthEvent, Long> {
    List<GrowthEvent> findAllByGardenIdOrderByIdDesc(Long gardenId);
}
