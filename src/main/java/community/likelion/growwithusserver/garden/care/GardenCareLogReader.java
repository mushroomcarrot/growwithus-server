package community.likelion.growwithusserver.garden.care;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 돌봄 기록(관리사 작업 완료) 도메인과의 접점. 그 도메인이 생기면 구현체를 @Component로 등록한다.
// 그러면 기본 빈(GardenCareLogConfiguration)은 빠진다.
public interface GardenCareLogReader {

    Optional<GardenCareLog> findCareLog(Long careLogId);

    // 텃밭의 가장 최근 돌봄 기록
    Optional<GardenCareLog> findLatest(Long gardenId);

    // from부터 to까지(양 끝 포함)의 돌봄 기록. 순서는 정하지 않는다
    List<GardenCareLog> findByGarden(Long gardenId, LocalDate from, LocalDate to);

    int countByGarden(Long gardenId);
}
