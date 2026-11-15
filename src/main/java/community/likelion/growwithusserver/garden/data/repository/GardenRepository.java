package community.likelion.growwithusserver.garden.data.repository;

import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GardenRepository extends JpaRepository<Garden, Long> {
    List<Garden> findAllByCaretakerUsername(String username);

    // 화면 조회용. 주인, 칸, 농장, 작물, 담당 관리사를 한 번에 가져온다
    @Query("select g from Garden g join fetch g.owner join fetch g.plot p join fetch p.farm "
            + "join fetch g.crop left join fetch g.caretaker where g.id = :id")
    Optional<Garden> findDetail(Long id);

    // XP, 스트릭처럼 값을 읽고 고치는 작업용. 같은 텃밭의 동시 요청이 차례로 처리되게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Garden g where g.id = :id")
    Optional<Garden> findByIdForUpdate(Long id);

    Optional<Garden> findByOwnerUsername(String username);

    // 칸의 텃밭. 분양된 칸에 담당 관리사를 뒤늦게 배정할 때 잠그고 고친다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Garden g where g.plot.id = :plotId")
    Optional<Garden> findByPlotIdForUpdate(Long plotId);

    // 주인의 텃밭 PK. 잠그기 전에 엔티티를 읽어 두지 않으려고 PK만 가져온다 (GardenService.findMyGardenForUpdate)
    @Query("select g.id from Garden g where g.owner.username = :username")
    Optional<Long> findIdByOwnerUsername(String username);

    // 한 농장의 텃밭 전부. 리그 순위를 낼 때 쓴다
    @Query("select g from Garden g join fetch g.owner join g.plot p where p.farm.id = :farmId order by g.id asc")
    List<Garden> findAllByFarmId(Long farmId);

    // 텃밭이 하나라도 있는 농장의 PK
    @Query("select distinct p.farm.id from Garden g join g.plot p")
    List<Long> findFarmIdsWithGarden();

    // 스트릭이 이어지는 중인데 그날 아직 응원하지 않은 텃밭 (스트릭 위험 알림 대상)
    @Query("select g from Garden g join fetch g.owner where g.streakDays > 0 "
            + "and (g.lastCheeredOn is null or g.lastCheeredOn < :date) order by g.id asc")
    List<Garden> findStreaksAtRisk(LocalDate date);

    // 전날 응원이 없는 텃밭의 스트릭을 끊는다. date보다 앞서 마지막으로 응원한 텃밭이 대상이다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Garden g set g.streakDays = 0, g.mood = :mood "
            + "where g.streakDays > 0 and (g.lastCheeredOn is null or g.lastCheeredOn < :date)")
    int breakStreaks(LocalDate date, CharacterMood mood);
}
