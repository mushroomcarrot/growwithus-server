package community.likelion.growwithusserver.garden.data.repository;

import community.likelion.growwithusserver.garden.data.entity.Garden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GardenRepository extends JpaRepository<Garden, Long> {

    // 화면 조회용. 주인, 칸, 농장, 작물, 담당 관리사를 한 번에 가져온다
    @Query("select g from Garden g join fetch g.owner join fetch g.plot p join fetch p.farm "
            + "join fetch g.crop left join fetch g.caretaker where g.id = :id")
    Optional<Garden> findDetail(Long id);

    Optional<Garden> findByOwnerUsername(String username);
}
