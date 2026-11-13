package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.ai.logic.GrowthCalculator.Stage;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component @RequiredArgsConstructor @Transactional(readOnly = true)
public class GardenCareReader implements CareGardenReader {
    private final GardenRepository gardens;
    private final OwnerProfileRepository owners;

    public List<CareGardenReader.Garden> findAssigned(String username) {
        if (username == null) return List.of();
        return gardens.findAllByCaretakerUsername(username).stream().map(this::map).toList();
    }
    public List<CareGardenReader.Garden> findAll() {
        return gardens.findAll().stream().filter(g -> g.getCaretaker() != null).map(this::map).toList();
    }
    public Optional<CareGardenReader.Garden> findGarden(Long id) { return gardens.findDetail(id).map(this::map); }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<CareGardenReader.Garden> findGardenForUpdate(Long id) {
        return gardens.findByIdForUpdate(id).map(this::map);
    }
    private CareGardenReader.Garden map(community.likelion.growwithusserver.garden.data.entity.Garden garden) {
        var farm = garden.getPlot().getFarm();
        var crop = garden.getCrop();
        var nickname = owners.findByUserId(garden.getOwner().getId()).map(p -> p.getNickname()).orElse(garden.getOwner().getName());
        return new CareGardenReader.Garden(garden.getId(),
                garden.getCaretaker() == null ? null : garden.getCaretaker().getUsername(), garden.getOwner().getUsername(),
                garden.getPlot().getPlotCode(), crop.getName(), nickname, farm.getNx(), farm.getNy(),
                Stage.valueOf(garden.getStage().name()), null, List.of(), garden.getStartedOn(), crop.getBaseTemp(),
                Map.of(Stage.SEED, 0d, Stage.SPROUT, crop.getSproutGdd(), Stage.FLOWER, crop.getFlowerGdd(), Stage.FRUIT, crop.getFruitGdd()));
    }
}
