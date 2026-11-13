package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.user.profile.OwnerSummaryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// GET /users/me의 owner 요약과 역할 변경 시 프로필 복원 여부를 회원 도메인에 준다.
@Component
@RequiredArgsConstructor
public class GardenOwnerSummaryReader implements OwnerSummaryReader {
    private final OwnerProfileRepository ownerProfileRepository;
    private final GardenRepository gardenRepository;
    private final community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository hearts;

    @Override
    @Transactional(readOnly = true)
    public Optional<Summary> findByUsername(String username) {
        return ownerProfileRepository.findByUserUsername(username)
                .map(profile -> new Summary(
                        profile.getNickname(),
                        profile.getDistrict().name(),
                        gardenRepository.findByOwnerUsername(username).map(garden -> garden.getId()).orElse(null),
                        profile.getFamilySize(), hearts.countByUserUsername(username)));
    }
}
