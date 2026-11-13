package community.likelion.growwithusserver.farm.data.repository;

import community.likelion.growwithusserver.farm.data.entity.Crop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropRepository extends JpaRepository<Crop, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Crop c where c.id = :id")
    Optional<Crop> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<Crop> findAllByOrderByIdAsc();

    Optional<Crop> findByName(String name);
}
