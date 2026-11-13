package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 텃밭 API가 공통으로 쓰는 텃밭 조회와 소유 확인. 호출한 서비스 메서드의 트랜잭션 안에서 쓴다.
 *
 * XP, 스트릭, 보상처럼 값을 읽고 고치는 작업은 텃밭을 먼저 잠그고(ForUpdate) 그 뒤에 상태를 읽는다.
 * 잠그기 전에 읽은 값으로 판단하면 동시에 들어온 요청이 보상을 두 번 받을 수 있다.
 */
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

    // 텃밭 ID를 모르는 API(퀘스트 보상, 하트, 퀴즈 답)가 주인의 텃밭을 먼저 잠글 때 쓴다. 주인 한 명에 텃밭은 하나다
    public Optional<Garden> findMyGardenForUpdate(String username) {
        return gardenRepository.findIdByOwnerUsername(username).flatMap(gardenRepository::findByIdForUpdate);
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
