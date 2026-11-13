package community.likelion.growwithusserver.garden.data.repository;

import community.likelion.growwithusserver.garden.data.entity.Garden;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GardenRepository extends JpaRepository<Garden, Long> {

    // 화면 조회용. 주인, 칸, 농장, 작물, 담당 관리사를 한 번에 가져온다
    @Query("select g from Garden g join fetch g.owner join fetch g.plot p join fetch p.farm "
            + "join fetch g.crop left join fetch g.caretaker where g.id = :id")
    Optional<Garden> findDetail(Long id);

    // XP, 스트릭처럼 값을 읽고 고치는 작업용. 같은 텃밭의 동시 요청이 차례로 처리되게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Garden g where g.id = :id")
    Optional<Garden> findByIdForUpdate(Long id);

    Optional<Garden> findByOwnerUsername(String username);
}
