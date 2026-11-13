package community.likelion.growwithusserver.caretaker.farm;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.farm.caretaker.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
@Component @RequiredArgsConstructor
public class StoredFarmCaretakerReader implements FarmCaretakerReader {
    private final CaretakerProfileRepository profiles;
    @Transactional(readOnly = true)
    public Optional<FarmCaretaker> findByUsername(String username) {
        return profiles.findByUsername(username).map(p -> new FarmCaretaker(p.getId(), p.getName(), p.getAvatarUrl()));
    }
}
