package community.likelion.growwithusserver.user.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.user.data.dto.request.RoleChangeRequestDto;
import community.likelion.growwithusserver.user.data.dto.response.RoleChangeResponseDto;
import community.likelion.growwithusserver.user.data.dto.response.UserMeResponseDto;
import community.likelion.growwithusserver.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보와 역할 (홈 분기 기준)")
    public GlobalResponse<UserMeResponseDto> getMe() {
        return userService.getMe(SecurityUtil.getUsername());
    }

    @PutMapping("/me/role")
    @Operation(summary = "역할 선택 및 변경 (새 토큰 발급)")
    public GlobalResponse<RoleChangeResponseDto> changeRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
                                                            @Valid @RequestBody RoleChangeRequestDto roleChangeRequestDto) {
        return userService.changeRole(SecurityUtil.getUsername(), bearerToken, roleChangeRequestDto);
    }
}
