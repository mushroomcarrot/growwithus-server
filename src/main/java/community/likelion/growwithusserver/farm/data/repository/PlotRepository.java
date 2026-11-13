package community.likelion.growwithusserver.farm.data.repository;

import community.likelion.growwithusserver.farm.data.entity.Plot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlotRepository extends JpaRepository<Plot, Long> {

    @Query("select p from Plot p join fetch p.farm join fetch p.crop left join fetch p.caretaker "
            + "where p.farm.id = :farmId and p.plotCode = :plotCode")
    Optional<Plot> findPlot(Long farmId, String plotCode);

    // 분양 처리용. 같은 칸을 동시에 분양받지 못하게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plot p where p.farm.id = :farmId and p.plotCode = :plotCode")
    Optional<Plot> findPlotForUpdate(Long farmId, String plotCode);

    boolean existsByFarmIdAndPlotCode(Long farmId, String plotCode);

    // 운영자용 칸 목록. 작물과 담당 관리사를 한 번에 가져온다
    @Query("select p from Plot p join fetch p.crop left join fetch p.caretaker "
            + "where p.farm.id = :farmId order by p.plotCode asc")
    List<Plot> findAllByFarm(Long farmId);
}
