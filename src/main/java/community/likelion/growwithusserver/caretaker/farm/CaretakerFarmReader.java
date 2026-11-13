package community.likelion.growwithusserver.caretaker.farm;
import java.util.Optional;
public interface CaretakerFarmReader {
    record Farm(Long farmId, String name) {}
    Optional<Farm> findFarm(Long id);
}
