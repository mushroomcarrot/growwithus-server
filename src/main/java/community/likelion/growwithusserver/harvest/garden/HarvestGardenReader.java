package community.likelion.growwithusserver.harvest.garden;

import java.util.Optional;

// 텃밭 도메인과의 접점. 텃밭, 농장, 관리사, 성장 시뮬레이션 도메인이 생기면
// 이 인터페이스의 구현체를 @Component로 등록한다. 그러면 기본 빈(HarvestGardenConfiguration)은 빠진다.
public interface HarvestGardenReader {

    // 텃밭이 없으면 비어 있다
    Optional<HarvestGarden> findGarden(Long gardenId);
}
