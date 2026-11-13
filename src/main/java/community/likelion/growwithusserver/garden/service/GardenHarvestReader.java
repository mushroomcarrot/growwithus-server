package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.farm.caretaker.FarmCaretakerReader;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.care.GardenCareLogReader;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.harvest.garden.HarvestGarden;
import community.likelion.growwithusserver.harvest.garden.HarvestGardenReader;
import community.likelion.growwithusserver.user.data.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 수확 안내, 방문 예약, 나들이 추천에 텃밭 정보를 준다. 이 구현체가 등록되어 그 API들이 실제 텃밭으로 동작한다.
@Component
@RequiredArgsConstructor
public class GardenHarvestReader implements HarvestGardenReader {
    private final GardenRepository gardenRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final FarmCaretakerReader farmCaretakerReader;
    private final GardenCareLogReader gardenCareLogReader;
    private final community.likelion.growwithusserver.growth.service.GrowthStatusService growth;

    @Override
    @Transactional(readOnly = true)
    public Optional<HarvestGarden> findGarden(Long gardenId) {
        return gardenRepository.findDetail(gardenId).map(this::toHarvestGarden);
    }

    private HarvestGarden toHarvestGarden(Garden garden) {
        Farm farm = garden.getPlot().getFarm();
        User caretaker = garden.getCaretaker();

        return HarvestGarden.builder()
                .gardenId(garden.getId())
                .stage(garden.getStage())
                .ownerUsername(garden.getOwner().getUsername())
                .familySize(ownerProfileRepository.findByUserId(garden.getOwner().getId()).map(OwnerProfile::getFamilySize).orElse(1))
                .ownerNickname(ownerProfileRepository.findByUserId(garden.getOwner().getId())
                        .map(OwnerProfile::getNickname)
                        .orElse(garden.getOwner().getName()))
                .characterName(garden.getCharacterName())
                .farmId(farm.getId())
                .farmLatitude(farm.getLatitude())
                .farmLongitude(farm.getLongitude())
                .caretakerUsername(caretaker == null ? null : caretaker.getUsername())
                .caretakerName(caretaker == null ? null : farmCaretakerReader.findByUsername(caretaker.getUsername())
                        .map(profile -> profile.name())
                        .orElse(caretaker.getName()))
                .startedOn(garden.getStartedOn())
                .expectedHarvestDate(garden.getExpectedHarvestDate())
                .harvestable(growth.harvestable(garden.getId(), garden.getStage() == GrowthStage.FRUIT))
                .careLogCount(gardenCareLogReader.countByGarden(garden.getId()))
                .build();
    }
}
