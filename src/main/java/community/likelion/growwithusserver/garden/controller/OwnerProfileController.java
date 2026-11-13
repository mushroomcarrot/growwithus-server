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
public class OwnerProfileController {
    private final OwnerProfileService ownerProfileService;

    @PostMapping
    @Operation(summary = "주인 프로필 등록 및 텃밭 시작 (칸 분양, 텃밭 생성)")
    public GlobalResponse<OwnerProfileCreateResponseDto> register(@Valid @RequestBody OwnerProfileRequestDto ownerProfileRequestDto) {
        return ownerProfileService.register(SecurityUtil.getUsername(), ownerProfileRequestDto);
    }

    @PatchMapping
    @Operation(summary = "주인 프로필 수정 (바꿀 필드만)")
    public GlobalResponse<OwnerProfileResponseDto> update(@Valid @RequestBody OwnerProfileUpdateRequestDto ownerProfileUpdateRequestDto) {
        return ownerProfileService.update(SecurityUtil.getUsername(), ownerProfileUpdateRequestDto);
    }
}
