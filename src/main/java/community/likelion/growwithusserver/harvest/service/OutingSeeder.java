package community.likelion.growwithusserver.harvest.service;

import community.likelion.growwithusserver.harvest.data.entity.Outing;
import community.likelion.growwithusserver.harvest.data.enums.OutingType;
import community.likelion.growwithusserver.harvest.data.repository.OutingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 나들이 추천 시드 데이터. outing 테이블이 비어 있을 때만 넣는다. 출처와 고른 기준은 docs/public-data.md를 본다.
 *
 * 1. 체험농장: 전국농어촌체험휴양마을표준데이터의 처인구 3곳 (좌표는 원본 값)
 * 2. 식당: 모범음식점정보에서 처인구, 영업 중, 지정 유지인 곳 중 좌표를 확인한 4곳.
 *    원본에 좌표가 없어 OpenStreetMap에서 찾았다. 교동두부와 중앙식당은 가게 위치, 국수59와 백암제일식당은 가게가 있는 길의 위치다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutingSeeder implements ApplicationRunner {
    private final OutingRepository outingRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (outingRepository.count() > 0) {
            return;
        }

        List<Outing> outings = List.of(
                outing(OutingType.EXPERIENCE_FARM, "연미향마을", "경기도 용인시 처인구 원삼면 백원로 128-7",
                        "옥수수 따기, 고구마 캐기, 두부 만들기, 천연 염색", 37.15619079, 127.330525),
                outing(OutingType.EXPERIENCE_FARM, "장촌마을", "경기도 용인시 처인구 이동읍 이원로601번길 36",
                        "감자 캐기, 김장 체험, 표고버섯 종균 넣기, 꿀벌 관찰", 37.16434182, 127.2522907),
                outing(OutingType.EXPERIENCE_FARM, "학일마을", "경기도 용인시 처인구 원삼면 학일로 117-1",
                        "손 모내기, 딸기 따기, 장 담그기, 인절미 떡메치기", 37.14671639, 127.2858127),
                outing(OutingType.RESTAURANT, "교동두부", "경기도 용인시 처인구 양지읍 양대로 37",
                        "모범음식점, 한정식과 두부", 37.2364208, 127.2873053),
                outing(OutingType.RESTAURANT, "국수59", "경기도 용인시 처인구 양지읍 양지로143번길 5-9",
                        "모범음식점, 국수", 37.2361290, 127.2835254),
                outing(OutingType.RESTAURANT, "중앙식당", "경기도 용인시 처인구 백암면 근창로 13",
                        "모범음식점, 백암 순대국", 37.1646156, 127.3731447),
                outing(OutingType.RESTAURANT, "백암제일식당", "경기도 용인시 처인구 백암면 백암로201번길 11",
                        "모범음식점, 백암 순대국", 37.1667963, 127.3720192)
        );
        outingRepository.saveAll(outings);
        log.info("[run] 나들이 추천 시드 데이터 적재: {}건", outings.size());
    }

    private Outing outing(OutingType type, String name, String address, String description, double latitude, double longitude) {
        return Outing.builder()
                .type(type)
                .name(name)
                .address(address)
                .description(description)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }
}
