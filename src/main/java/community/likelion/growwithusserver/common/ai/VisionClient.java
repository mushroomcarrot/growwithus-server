package community.likelion.growwithusserver.common.ai;
import java.util.Optional;
public interface VisionClient {
    record Check(boolean matched, String message) {}
    Optional<Check> check(byte[] image, String taskDescription);
}
