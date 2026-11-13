package community.likelion.growwithusserver.caretaker.service;

import community.likelion.growwithusserver.caretaker.data.dto.CaretakerDtos;
import community.likelion.growwithusserver.caretaker.data.entity.CaretakerProfile;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.caretaker.farm.CaretakerFarmReader;
import community.likelion.growwithusserver.care.data.repository.CareLogRepository;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.*;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.enums.NextStep;
import community.likelion.growwithusserver.user.service.UserService;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor
public class CaretakerService {
    private final UserService users;
    private final CaretakerProfileRepository profiles;
    private final CaretakerFarmReader farms;
    private final CareLogRepository logs;
    private final community.likelion.growwithusserver.garden.data.repository.CareLogHeartRepository hearts;
    @Value("${care.allowance-per-task:0}") private long allowancePerTask;
    @Value("${file.base-url}") private String fileBaseUrl;

    @Transactional
    public GlobalResponse<CaretakerDtos.Profile> register(String username, CaretakerDtos.Register request) {
        if (request == null || request.getName() == null || request.getName().isBlank() || request.getName().length() > 20) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        var user = users.getUserForUpdate(username);
        if (user.getRole() == UserRole.USER) throw new VoiceMemoException(ErrorCode.ROLE_NOT_SELECTED);
        if (user.getRole() != UserRole.CARETAKER && user.getRole() != UserRole.ADMIN) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        if (profiles.existsByUsername(username)) throw new VoiceMemoException(ErrorCode.PROFILE_ALREADY_EXISTS);
        Long farmId = IdPrefix.FARM.parse(request.getFarmId());
        var farm = farms.findFarm(farmId).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!farmId.equals(farm.farmId())) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        var profile = profiles.save(CaretakerProfile.builder().userId(user.getId()).username(username)
                .name(request.getName().strip()).farmId(farmId).avatarUrl(fileBaseUrl.replaceAll("/$", "") + "/images/caretaker-default.svg")
                .portraitConsent(false).createdAt(LocalDateTime.now()).build());
        user.setProfileRegistered(true);
        return GlobalResponse.ok(CaretakerDtos.Profile.builder().caretakerId(IdPrefix.CARETAKER.format(profile.getId()))
                .name(profile.getName()).farmId(IdPrefix.FARM.format(profile.getFarmId()))
                .nextStep(user.getRole() == UserRole.ADMIN ? NextStep.CARE_TODAY : users.resolveNextStep(user)).build());
    }

    @Transactional(readOnly = true)
    public GlobalResponse<CaretakerDtos.Summary> summary(String username, String month) {
        profiles.findByUsername(username).orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (month != null && !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        YearMonth selected;
        try { selected = month == null ? YearMonth.now() : YearMonth.parse(month); }
        catch (java.time.format.DateTimeParseException ex) { throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER); }
        if (selected.equals(YearMonth.of(9999, 12))) throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        if (allowancePerTask < 0 || allowancePerTask > 1_000_000) throw new IllegalStateException("수당 가정 단가 설정이 유효하지 않습니다.");
        var records = logs.findAllByCaretakerUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(username,
                selected.atDay(1).atStartOfDay(), selected.plusMonths(1).atDay(1).atStartOfDay());
        return GlobalResponse.ok(CaretakerDtos.Summary.builder().month(selected.toString()).completedTasks(records.size())
                .gardenCount(records.stream().map(r -> r.getGardenId()).distinct().count())
                .workDays(records.stream().map(r -> r.getCreatedAt().toLocalDate()).distinct().count())
                .heartsReceived(hearts.countByCaretakerUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(username,
                        selected.atDay(1).atStartOfDay(), selected.plusMonths(1).atDay(1).atStartOfDay()))
                .expectedAllowance(Math.multiplyExact((long) records.size(), allowancePerTask))
                .allowanceNote("가정치예요. 실제 수당과 다를 수 있어요.").build());
    }
}
