package community.likelion.growwithusserver.user.service;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.AuthenticationToken;
import community.likelion.growwithusserver.common.security.JwtTokenProvider;
import community.likelion.growwithusserver.common.security.exception.TokenInvalidException;
import community.likelion.growwithusserver.user.data.dto.request.LoginRequestDto;
import community.likelion.growwithusserver.user.data.dto.request.RefreshRequestDto;
import community.likelion.growwithusserver.user.data.dto.request.SignUpRequestDto;
import community.likelion.growwithusserver.user.data.dto.response.AuthResponseDto;
import community.likelion.growwithusserver.user.data.dto.response.TokenRefreshResponseDto;
import community.likelion.growwithusserver.user.data.dto.response.UsernameCheckResponseDto;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.exception.PasswordInvalidException;
import community.likelion.growwithusserver.user.data.exception.UsernameDuplicationException;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    private void validatePassword(String originalPassword, String password) {
        if (!passwordEncoder.matches(originalPassword, password)) {
            throw new PasswordInvalidException();
        }
    }

    private AuthResponseDto createAuthResponse(User user, AuthenticationToken authenticationToken) {
        return AuthResponseDto.builder()
                .userId(user.getPublicId())
                .accessToken(authenticationToken.getAccessToken())
                .refreshToken(authenticationToken.getRefreshToken())
                .expiresIn(authenticationToken.getExpiresIn())
                .role(user.getRole())
                .nextStep(userService.resolveNextStep(user))
                .build();
    }

    @Transactional
    public GlobalResponse<AuthResponseDto> signUp(SignUpRequestDto signUpRequestDto) {
        if (userRepository.existsByUsername(signUpRequestDto.getUsername())) {
            log.error("[signUp] 중복된 아이디로 인한 가입 거부: {}", signUpRequestDto.getUsername());
            throw new UsernameDuplicationException();
        }

        // 가입 직후 역할은 USER다. 역할은 PUT /users/me/role로 고른다.
        User user = User.builder()
                .username(signUpRequestDto.getUsername())
                .name(signUpRequestDto.getName())
                .password(passwordEncoder.encode(signUpRequestDto.getPassword()))
                .role(UserRole.USER)
                .registeredAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        try {
            userRepository.save(user);
            log.info("[signUp] 새로운 사용자 등록: {}", user.getUsername());
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 같은 아이디가 먼저 들어온 경우
            log.error("[signUp] 중복된 아이디로 인한 가입 거부: {}", signUpRequestDto.getUsername());
            throw new UsernameDuplicationException();
        }

        return GlobalResponse.ok(createAuthResponse(user, userService.issueToken(user)));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<UsernameCheckResponseDto> checkUsername(String username) {
        return GlobalResponse.ok(UsernameCheckResponseDto.builder()
                .username(username)
                .available(!userRepository.existsByUsername(username))
                .build());
    }

    public GlobalResponse<AuthResponseDto> login(LoginRequestDto loginRequestDto) {
        User user = userService.getUserEntity(loginRequestDto.getUsername());
        validatePassword(loginRequestDto.getPassword(), user.getPassword());
        log.info("[login] 로그인: {}", user.getUsername());

        return GlobalResponse.ok(createAuthResponse(user, userService.issueToken(user)));
    }

    public GlobalResponse<TokenRefreshResponseDto> refresh(RefreshRequestDto refreshRequestDto) {
        String refreshToken = refreshRequestDto.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            log.error("[refresh] 유효하지 않은 Refresh Token으로 재발급 시도");
            throw new TokenInvalidException();
        }

        String username = tokenProvider.getUsername(refreshToken);
        if (!tokenProvider.isStoredRefreshToken(username, refreshToken)) {
            log.error("[refresh] 저장된 Refresh Token과 불일치하여 재발급 거부: {}", username);
            throw new TokenInvalidException();
        }

        User user = userService.getUserEntity(username);
        AuthenticationToken authenticationToken = userService.issueToken(user);
        log.info("[refresh] 토큰 재발급 완료: {}", username);

        return GlobalResponse.ok(TokenRefreshResponseDto.builder()
                .accessToken(authenticationToken.getAccessToken())
                .refreshToken(authenticationToken.getRefreshToken())
                .expiresIn(authenticationToken.getExpiresIn())
                .build());
    }

    public GlobalResponse<Void> logout(String bearerToken) {
        String accessToken = tokenProvider.resolveToken(bearerToken);

        if (accessToken == null || !tokenProvider.validateToken(accessToken)) {
            log.error("[logout] 유효하지 않은 Access Token으로 로그아웃 시도");
            throw new TokenInvalidException();
        }

        tokenProvider.invalidateToken(accessToken);
        log.info("[logout] 로그아웃 처리 완료");

        return GlobalResponse.ok();
    }
}
