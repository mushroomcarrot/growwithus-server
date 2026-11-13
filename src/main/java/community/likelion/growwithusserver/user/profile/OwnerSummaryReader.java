package community.likelion.growwithusserver.user.profile;
import java.util.Optional;
public interface OwnerSummaryReader {
    record Summary(String nickname, String district, Long gardenId) {}
    Optional<Summary> findByUsername(String username);
}
