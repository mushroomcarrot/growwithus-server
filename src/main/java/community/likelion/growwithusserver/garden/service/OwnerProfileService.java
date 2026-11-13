package community.likelion.growwithusserver.garden.service;

import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.exception.PlotAlreadyAssignedException;
import community.likelion.growwithusserver.farm.data.exception.PlotNotFoundException;
import community.likelion.growwithusserver.farm.data.repository.PlotRepository;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileCreateResponseDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileRequestDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileResponseDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileUpdateRequestDto;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.entity.OwnerProfile;
import community.likelion.growwithusserver.garden.data.exception.OwnerDistrictNotAllowedException;
import community.likelion.growwithusserver.garden.data.exception.OwnerProfileNotFoundException;
import community.likelion.growwithusserver.garden.data.exception.OwnerRoleRequiredException;
import community.likelion.growwithusserver.garden.data.exception.ProfileAlreadyExistsException;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.garden.data.repository.OwnerProfileRepository;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerProfileService {
    private final UserService userService;
    private final OwnerProfileRepository ownerProfileRepository;
    private final GardenRepository gardenRepository;
    private final PlotRepository plotRepository;

    // 프로필 등록, 칸 분양, 텃밭 생성을 한 트랜잭션에서 처리한다
    @Transactional
    public GlobalResponse<OwnerProfileCreateResponseDto> register(String username, OwnerProfileRequestDto ownerProfileRequestDto) {
        User user = userService.getUserEntity(username);
        if (user.getRole() == UserRole.USER) {
            log.error("[register] 역할 선택 전 주인 프로필 등록 시도: {}", username);
            throw new OwnerRoleRequiredException();
        }
        if (ownerProfileRepository.existsByUserId(user.getId())) {
            log.error("[register] 이미 주인 프로필이 있는 사용자: {}", username);
            throw new ProfileAlreadyExistsException();
        }
        requireOwnerDistrict(ownerProfileRequestDto.getDistrict());

        // 같은 칸을 동시에 분양받지 못하게 칸을 잠그고 확인한다
        Long farmId = IdPrefix.FARM.parse(ownerProfileRequestDto.getFarmId());
        Plot plot = plotRepository.findPlotForUpdate(farmId, ownerProfileRequestDto.getPlotCode())
                .orElseThrow(() -> {
                    log.error("[register] 칸 조회 실패: {} ({})", ownerProfileRequestDto.getFarmId(), ownerProfileRequestDto.getPlotCode());
                    return new PlotNotFoundException();
                });
        if (plot.isAssigned()) {
            log.error("[register] 이미 분양된 칸: {} ({})", ownerProfileRequestDto.getFarmId(), ownerProfileRequestDto.getPlotCode());
            throw new PlotAlreadyAssignedException();
        }
        plot.setAssigned(true);

        LocalDateTime now = LocalDateTime.now();
        Garden garden;
        try {
            ownerProfileRepository.saveAndFlush(OwnerProfile.builder()
                    .user(user)
                    .nickname(ownerProfileRequestDto.getNickname().strip())
                    .familySize(ownerProfileRequestDto.getFamilySize() == null ? 1 : ownerProfileRequestDto.getFamilySize())
                    .district(ownerProfileRequestDto.getDistrict())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            garden = gardenRepository.saveAndFlush(Garden.builder()
                    .owner(user)
                    .plot(plot)
                    .crop(plot.getCrop())
                    .caretaker(plot.getCaretaker())
                    .characterName(plot.getCrop().getCharacterName())
                    .startedOn(LocalDate.now())
                    .createdAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자가 동시에 두 번 등록한 경우 (user_id 유니크)
            log.error("[register] 주인 프로필 중복 등록: {}", username);
            throw new ProfileAlreadyExistsException();
        }

        user.setProfileRegistered(true);
        log.info("[register] 주인 프로필 등록 및 텃밭 시작: {} ({})", username, garden.getId());

        return GlobalResponse.ok(OwnerProfileCreateResponseDto.builder()
                .familySize(ownerProfileRequestDto.getFamilySize() == null ? 1 : ownerProfileRequestDto.getFamilySize())
                .gardenId(IdPrefix.GARDEN.format(garden.getId()))
                .characterName(garden.getCharacterName())
                .stage(garden.getStage())
                .nextStep(userService.resolveNextStep(user))
                .build());
    }

    @Transactional
    public GlobalResponse<OwnerProfileResponseDto> update(String username, OwnerProfileUpdateRequestDto ownerProfileUpdateRequestDto) {
        OwnerProfile profile = ownerProfileRepository.findByUserUsername(username)
                .orElseThrow(() -> {
                    log.error("[update] 주인 프로필 조회 실패: {}", username);
                    return new OwnerProfileNotFoundException();
                });

        if (ownerProfileUpdateRequestDto.getNickname() != null) {
            profile.setNickname(ownerProfileUpdateRequestDto.getNickname().strip());
        }
        if (ownerProfileUpdateRequestDto.getDistrict() != null) {
            requireOwnerDistrict(ownerProfileUpdateRequestDto.getDistrict());
            profile.setDistrict(ownerProfileUpdateRequestDto.getDistrict());
        }
        profile.setUpdatedAt(LocalDateTime.now());
        if (ownerProfileUpdateRequestDto.getFamilySize() != null) profile.setFamilySize(ownerProfileUpdateRequestDto.getFamilySize());

        return GlobalResponse.ok(OwnerProfileResponseDto.builder()
                .nickname(profile.getNickname())
                .familySize(profile.getFamilySize())
                .district(profile.getDistrict())
                .gardenId(gardenRepository.findByOwnerUsername(username)
                        .map(garden -> IdPrefix.GARDEN.format(garden.getId()))
                        .orElse(null))
                .build());
    }

    // 주인은 수지구, 기흥구에 사는 도시민이다
    private void requireOwnerDistrict(District district) {
        if (district != District.SUJI && district != District.GIHEUNG) {
            log.error("[requireOwnerDistrict] 주인이 고를 수 없는 구: {}", district);
            throw new OwnerDistrictNotAllowedException();
        }
    }
}
