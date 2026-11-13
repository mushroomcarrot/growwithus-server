package community.likelion.growwithusserver.game.data.repository;

import community.likelion.growwithusserver.game.data.entity.LeagueMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, Long> {
}
