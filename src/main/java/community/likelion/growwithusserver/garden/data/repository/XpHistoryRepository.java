package community.likelion.growwithusserver.garden.data.repository;

import community.likelion.growwithusserver.garden.data.entity.XpHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface XpHistoryRepository extends JpaRepository<XpHistory, Long> {

    // from 이상 to 미만에 한 텃밭이 얻은 XP
    @Query("select coalesce(sum(x.amount), 0) from XpHistory x "
            + "where x.garden.id = :gardenId and x.createdAt >= :from and x.createdAt < :to")
    long sumAmount(Long gardenId, LocalDateTime from, LocalDateTime to);

    // 여러 텃밭이 같은 기간에 얻은 XP. 한 줄이 {텃밭 PK, 합계}이고 기록이 없는 텃밭은 빠진다
    @Query("select x.garden.id, sum(x.amount) from XpHistory x "
            + "where x.garden.id in :gardenIds and x.createdAt >= :from and x.createdAt < :to group by x.garden.id")
    List<Object[]> sumAmountByGarden(List<Long> gardenIds, LocalDateTime from, LocalDateTime to);
}
