package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 텃밭 API가 공통으로 쓰는 텃밭 조회와 소유 확인. 호출한 서비스 메서드의 트랜잭션 안에서 쓴다.
@Service
@RequiredArgsConstructor
@Slf4j
public class GardenService {
    private final GardenRepository gardenRepository;

    // 외부 ID(gdn_3)로 내 텃밭을 찾는다. 없으면 C404, 내 텃밭이 아니면 C403
    public Garden getOwnedGarden(String username, String gardenId) {
        Garden garden = getGardenEntity(IdPrefix.GARDEN.parse(gardenId));
        requireOwner(username, garden);

        return garden;
    }

    // 내 텃밭을 잠그고 가져온다. XP나 스트릭을 바꾸는 작업이 쓴다
    public Garden getOwnedGardenForUpdate(String username, String gardenId) {
        Garden garden = getGardenForUpdate(IdPrefix.GARDEN.parse(gardenId));
        requireOwner(username, garden);

        return garden;
    }

    public Garden getGardenForUpdate(Long id) {
        return gardenRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.error("[getGardenForUpdate] 텃밭 조회 실패: {}", id);
                    return new GardenNotFoundException();
                });
    }

    public Garden getGardenEntity(Long id) {
        return gardenRepository.findDetail(id)
                .orElseThrow(() -> {
                    log.error("[getGardenEntity] 텃밭 조회 실패: {}", id);
                    return new GardenNotFoundException();
                });
    }

    public void requireOwner(String username, Garden garden) {
        if (!garden.getOwner().getUsername().equals(username)) {
            log.error("[requireOwner] 내 텃밭이 아닌 텃밭 접근: {} ({})", username, garden.getId());
            throw new GardenAccessDeniedException();
        }
    }
}
