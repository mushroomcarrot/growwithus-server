package community.likelion.growwithusserver.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.user.data.dto.request.LoginRequestDto;
import community.likelion.growwithusserver.user.data.dto.request.RefreshRequestDto;
import community.likelion.growwithusserver.user.data.dto.request.SignUpRequestDto;
import community.likelion.growwithusserver.user.data.dto.response.AuthResponseDto;
import community.likelion.growwithusserver.user.data.dto.response.TokenRefreshResponseDto;
import community.likelion.growwithusserver.user.data.dto.response.UsernameCheckResponseDto;
import community.likelion.growwithusserver.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입 (이름, 아이디, 비밀번호)")
    public GlobalResponse<AuthResponseDto> signUp(@Valid @RequestBody SignUpRequestDto signUpRequestDto) {
        return authService.signUp(signUpRequestDto);
    }

    @GetMapping("/username-check")
    @Operation(summary = "아이디 중복 확인")
    public GlobalResponse<UsernameCheckResponseDto> checkUsername(@RequestParam String username) {
        return authService.checkUsername(username);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인, nextStep으로 이동 화면 안내")
    public GlobalResponse<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        return authService.login(loginRequestDto);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token으로 토큰 재발급")
    public GlobalResponse<TokenRefreshResponseDto> refresh(@Valid @RequestBody RefreshRequestDto refreshRequestDto) {
        return authService.refresh(refreshRequestDto);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public GlobalResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return authService.logout(bearerToken);
    }
}
