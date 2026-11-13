package community.likelion.growwithusserver.farm.data.repository;

import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.enums.District;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    // 등록순(id 오름차순), cursor보다 큰 id부터
    @Query("select f from Farm f where (:district is null or f.district = :district) "
            + "and (:cursorId is null or f.id > :cursorId) order by f.id asc")
    List<Farm> findPage(District district, Long cursorId, Pageable pageable);

    Optional<Farm> findByExternalId(String externalId);
}
