package community.likelion.growwithusserver.harvest.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.harvest.data.exception.GardenAccessDeniedException;
import community.likelion.growwithusserver.harvest.data.exception.GardenNotFoundException;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import community.likelion.growwithusserver.harvest.garden.HarvestGardenReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 수확, 방문 API가 공통으로 쓰는 텃밭 조회와 소유 확인
@Service
@RequiredArgsConstructor
@Slf4j
public class HarvestGardenService {
    private final HarvestGardenReader harvestGardenReader;

    // 외부 ID(gdn_3)로 내 텃밭을 찾는다. 없으면 C404, 내 텃밭이 아니면 C403
    public HarvestGarden getOwnedGarden(String username, String gardenId) {
        HarvestGarden garden = getGarden(IdPrefix.GARDEN.parse(gardenId));

        if (!username.equals(garden.getOwnerUsername())) {
            log.error("[getOwnedGarden] 내 텃밭이 아닌 텃밭 접근: {} ({})", username, gardenId);
            throw new GardenAccessDeniedException();
        }

        return garden;
    }

    public HarvestGarden getGarden(Long gardenId) {
        return harvestGardenReader.findGarden(gardenId)
                .orElseThrow(() -> {
                    log.error("[getGarden] 텃밭 조회 실패: {}", gardenId);
                    return new GardenNotFoundException();
                });
    }
}
