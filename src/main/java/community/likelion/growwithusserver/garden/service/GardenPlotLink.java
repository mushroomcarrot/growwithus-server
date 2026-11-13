package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.farm.data.exception.PlotAlreadyAssignedException;
import community.likelion.growwithusserver.farm.garden.PlotGardenLink;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 분양된 칸에 담당 관리사가 배정되면 그 칸의 텃밭에도 반영한다. 관리사의 오늘 할 일은 다음 조회 때 만들어진다.
@Slf4j
@Component
@RequiredArgsConstructor
public class GardenPlotLink implements PlotGardenLink {
    private final GardenRepository gardenRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignCaretaker(Long plotId, User caretaker) {
        gardenRepository.findByPlotIdForUpdate(plotId).ifPresent(garden -> {
            if (garden.getCaretaker() == null) {
                garden.setCaretaker(caretaker);
                log.info("[assignCaretaker] 텃밭에 담당 관리사 배정: {} ({})", garden.getId(), caretaker.getUsername());
            } else if (!garden.getCaretaker().getId().equals(caretaker.getId())) {
                log.error("[assignCaretaker] 이미 다른 관리사가 맡은 텃밭: {}", garden.getId());
                throw new PlotAlreadyAssignedException();
            }
        });
    }
}
