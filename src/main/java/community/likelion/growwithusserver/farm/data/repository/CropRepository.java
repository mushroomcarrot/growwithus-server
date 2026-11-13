package community.likelion.growwithusserver.farm.data.repository;

import community.likelion.growwithusserver.farm.data.entity.Crop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropRepository extends JpaRepository<Crop, Long> {
    List<Crop> findAllByOrderByIdAsc();

    Optional<Crop> findByName(String name);
}
