package community.likelion.growwithusserver.garden.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileCreateResponseDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileRequestDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileResponseDto;
import community.likelion.growwithusserver.garden.data.dto.OwnerProfileUpdateRequestDto;
import community.likelion.growwithusserver.garden.service.OwnerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/owners/me/profile")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class OwnerProfileController {
    private final OwnerProfileService ownerProfileService;
    private final community.likelion.growwithusserver.care.service.TaskGenerationService tasks;

    @PostMapping
    @Operation(summary = "주인 프로필 등록 및 텃밭 시작 (칸 분양, 텃밭 생성)")
    public GlobalResponse<OwnerProfileCreateResponseDto> register(@Valid @RequestBody OwnerProfileRequestDto ownerProfileRequestDto) {
        var result = ownerProfileService.register(SecurityUtil.getUsername(), ownerProfileRequestDto);
        // Profile registration has committed before this bounded, rule-only initialization.
        try {
            tasks.generateInitial(community.likelion.growwithusserver.common.id.IdPrefix.GARDEN.parse(result.getData().getGardenId()));
        } catch (RuntimeException ex) {
            log.warn("[register] 첫 작업 생성 보류, 오늘 할 일 조회에서 재시도: {}", ex.getClass().getSimpleName());
        }
        return result;
    }

    @PatchMapping
    @Operation(summary = "주인 프로필 수정 (바꿀 필드만)")
    public GlobalResponse<OwnerProfileResponseDto> update(@Valid @RequestBody OwnerProfileUpdateRequestDto ownerProfileUpdateRequestDto) {
        return ownerProfileService.update(SecurityUtil.getUsername(), ownerProfileUpdateRequestDto);
    }
}
