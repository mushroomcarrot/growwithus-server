package community.likelion.growwithusserver.user.profile;
import java.util.Optional;
public interface OwnerSummaryReader {
    record Summary(String nickname, String district, Long gardenId, int familySize, long sentHearts) {
        public Summary(String nickname, String district, Long gardenId) { this(nickname, district, gardenId, 1, 0); }
    }
    Optional<Summary> findByUsername(String username);
}
