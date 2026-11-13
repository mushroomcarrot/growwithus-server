package community.likelion.growwithusserver.caretaker.farm;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
@Component @RequiredArgsConstructor
public class StoredCaretakerFarmReader implements CaretakerFarmReader {
    private final FarmRepository farms;
    @Transactional(readOnly = true)
    public Optional<Farm> findFarm(Long id) { return farms.findById(id).map(f -> new Farm(f.getId(), f.getName())); }
}
