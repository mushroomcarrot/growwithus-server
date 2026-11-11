package community.likelion.growwithusserver.harvest.service;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.harvest.data.dto.HarvestResponseDto;
import community.likelion.growwithusserver.harvest.data.dto.OutingResponseDto;
import community.likelion.growwithusserver.harvest.data.entity.Outing;
import community.likelion.growwithusserver.harvest.data.repository.OutingRepository;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HarvestService {
    // 나들이 추천으로 내려주는 최대 개수
    static final int OUTING_LIMIT = 6;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final HarvestGardenService harvestGardenService;
    private final OutingRepository outingRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<HarvestResponseDto> getHarvest(String username, String gardenId) {
        HarvestGarden garden = harvestGardenService.getOwnedGarden(username, gardenId);
        LocalDate today = LocalDate.now();
        LocalDate expectedDate = garden.getExpectedHarvestDate();

        Integer dDay = expectedDate == null
                ? null
                : (int) Math.max(0, ChronoUnit.DAYS.between(today, expectedDate));
        int daysCaredTogether = garden.getStartedOn() == null
                ? 0
                : (int) Math.max(0, ChronoUnit.DAYS.between(garden.getStartedOn(), today) + 1);

        return GlobalResponse.ok(HarvestResponseDto.builder()
                .expectedDate(expectedDate)
                .dDay(dDay)
                .harvestable(garden.isHarvestable())
                .daysCaredTogether(daysCaredTogether)
                .careLogCount(garden.getCareLogCount())
                .caretakerName(garden.getCaretakerName())
                .message(createMessage(garden))
                .build());
    }

    // 농장에서 가까운 순으로 내려준다. 농장 위치를 모르면 거리 없이 등록 순으로 준다.
    @Transactional(readOnly = true)
    public GlobalResponse<List<OutingResponseDto>> getOutings(String username, String gardenId) {
        HarvestGarden garden = harvestGardenService.getOwnedGarden(username, gardenId);
        boolean hasLocation = garden.getFarmLatitude() != null && garden.getFarmLongitude() != null;

        List<OutingResponseDto> outings = outingRepository.findAll().stream()
                .map(outing -> toOutingResponse(outing, hasLocation
                        ? distanceKm(garden.getFarmLatitude(), garden.getFarmLongitude(), outing.getLatitude(), outing.getLongitude())
                        : null))
                .sorted(Comparator.comparing(OutingResponseDto::getDistanceKm, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(OUTING_LIMIT)
                .toList();

        return GlobalResponse.ok(outings);
    }

    private String createMessage(HarvestGarden garden) {
        String name = garden.getCharacterName() == null ? "작물" : garden.getCharacterName();

        if (garden.isHarvestable()) {
            return KoreanParticle.subject(name) + " 다 자랐어요. 주말에 만나러 오세요";
        }
        if (garden.getExpectedHarvestDate() == null) {
            return KoreanParticle.subject(name) + " 자라는 중이에요. 수확일을 곧 알려드릴게요";
        }

        LocalDate expectedDate = garden.getExpectedHarvestDate();
        return expectedDate.getMonthValue() + "월 " + expectedDate.getDayOfMonth() + "일쯤 " + KoreanParticle.object(name) + " 만나러 오세요";
    }

    private OutingResponseDto toOutingResponse(Outing outing, Double distanceKm) {
        return OutingResponseDto.builder()
                .type(outing.getType())
                .name(outing.getName())
                .distanceKm(distanceKm)
                .address(outing.getAddress())
                .description(outing.getDescription())
                .build();
    }

    // 두 좌표 사이의 직선 거리 (하버사인), 소수 첫째 자리
    static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double distance = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));

        return Math.round(distance * 10) / 10.0;
    }
}
