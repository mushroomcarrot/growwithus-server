package community.likelion.growwithusserver.farm.service;

import community.likelion.growwithusserver.farm.data.dto.FarmSyncResponseDto;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 주말농장 동기화 배치. 스케줄러(매주 월 03:00)와 POST /internal/farms/sync가 같은 메서드를 부른다.
 *
 * 공공데이터포털의 "경기도 용인시 주말농장 (2015.12)"은 오픈 API가 아니라 한 번 올라온 파일이라,
 * 그 파일을 저장소에 넣어 두고 (data/weekend-farms.csv) 읽는다. 운영자 이름과 연락처 열은 뺐다.
 * 원본에 좌표가 없어 주소의 읍면동 대표 좌표를 쓴다 (YonginTown).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FarmSyncService {
    public static final String EXTERNAL_ID_PREFIX = "yongin-weekend-farm:";
    private static final String CITY = "경기도 용인시";

    private final FarmRepository farmRepository;

    @Value("classpath:data/weekend-farms.csv")
    private Resource farmFile;

    @Transactional
    public FarmSyncResponseDto sync() {
        List<SourceFarm> sourceFarms = readSource();
        LocalDateTime now = LocalDateTime.now();

        int added = 0;
        int updated = 0;
        List<String> skipped = new ArrayList<>();
        for (SourceFarm source : sourceFarms) {
            Optional<YonginTown> town = YonginTown.find(source.location());
            if (town.isEmpty()) {
                log.warn("[sync] 읍면동을 찾지 못해 건너뜀: {} ({})", source.name(), source.location());
                skipped.add(source.name());
                continue;
            }

            Farm fresh = toFarm(source, town.get(), now);
            Optional<Farm> existing = farmRepository.findByExternalId(fresh.getExternalId());
            if (existing.isEmpty()) {
                farmRepository.save(fresh);
                added++;
            } else if (apply(existing.get(), fresh, now)) {
                updated++;
            }
        }

        log.info("[sync] 주말농장 동기화: 전체 {}곳, 추가 {}곳, 변경 {}곳, 건너뜀 {}곳",
                sourceFarms.size(), added, updated, skipped.size());

        return FarmSyncResponseDto.builder()
                .totalFarms(sourceFarms.size())
                .addedFarms(added)
                .updatedFarms(updated)
                .skippedFarms(skipped)
                .build();
    }

    private Farm toFarm(SourceFarm source, YonginTown town, LocalDateTime now) {
        int[] grid = KmaGrid.toGrid(town.latitude(), town.longitude());

        return Farm.builder()
                .name(source.name())
                .address(CITY + " " + town.district().getKoreanName() + " " + source.location())
                .latitude(town.latitude())
                .longitude(town.longitude())
                .district(town.district())
                .nx(grid[0])
                .ny(grid[1])
                .externalId(EXTERNAL_ID_PREFIX + source.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // 원본과 다른 값이 있으면 반영하고 true를 돌려준다
    private boolean apply(Farm farm, Farm fresh, LocalDateTime now) {
        boolean same = farm.getName().equals(fresh.getName())
                && farm.getAddress().equals(fresh.getAddress())
                && farm.getLatitude() == fresh.getLatitude()
                && farm.getLongitude() == fresh.getLongitude()
                && farm.getDistrict() == fresh.getDistrict()
                && farm.getNx() == fresh.getNx()
                && farm.getNy() == fresh.getNy();
        if (same) {
            return false;
        }

        farm.setName(fresh.getName());
        farm.setAddress(fresh.getAddress());
        farm.setLatitude(fresh.getLatitude());
        farm.setLongitude(fresh.getLongitude());
        farm.setDistrict(fresh.getDistrict());
        farm.setNx(fresh.getNx());
        farm.setNy(fresh.getNy());
        farm.setUpdatedAt(now);
        return true;
    }

    // 열: 시군구, 구분, 농장명, 위치. 첫 줄은 제목이다
    List<SourceFarm> readSource() {
        List<SourceFarm> farms = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(farmFile.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", -1);
                if (columns.length < 4 || columns[2].isBlank()) {
                    continue;
                }
                farms.add(new SourceFarm(columns[2].trim(), columns[3].trim()));
            }
        } catch (IOException e) {
            log.error("[readSource] 주말농장 원본 파일 읽기 실패");
            throw new UncheckedIOException(e);
        }

        return farms;
    }

    record SourceFarm(String name, String location) {
    }
}
