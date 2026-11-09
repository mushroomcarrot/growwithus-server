package community.likelion.growwithusserver.user.service;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.AuthenticationToken;
import community.likelion.growwithusserver.common.security.JwtTokenProvider;
import community.likelion.growwithusserver.common.security.exception.TokenInvalidException;
import community.likelion.growwithusserver.user.data.dto.request.ReissueRequestDto;
import community.likelion.growwithusserver.user.data.dto.request.SignInRequestDto;
import community.likelion.growwithusserver.user.data.dto.request.SignUpRequestDto;
import community.likelion.growwithusserver.user.data.dto.response.TokenResponseDto;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.exception.EmailDuplicationException;
import community.likelion.growwithusserver.user.data.exception.PasswordInvalidException;
import community.likelion.growwithusserver.user.data.exception.UserNotFoundException;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public User getUserEntity(String email) {
        log.info("[getUserEntity] 사용자 조회 시도: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("[getUserEntity] 사용자 조회 실패: {}", email);
                    return new UserNotFoundException();
                });
    }

    private void validatePassword(String originalPassword, String password) {
        if(!passwordEncoder.matches(originalPassword, password)) {
            throw new PasswordInvalidException();
        }
    }

    private Authentication createAuthentication(User user) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().getValue())
        );

        return new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
    }

    private TokenResponseDto createTokenResponse(User user, AuthenticationToken authenticationToken) {
        return TokenResponseDto.builder()
                .accessToken(authenticationToken.getAccessToken())
                .refreshToken(authenticationToken.getRefreshToken())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    @Transactional
    public GlobalResponse<TokenResponseDto> signUp(SignUpRequestDto signUpRequestDto) {
        User user = User.builder()
                .email(signUpRequestDto.getEmail())
                .name(signUpRequestDto.getName())
                .password(passwordEncoder.encode(signUpRequestDto.getPassword()))
                .role(signUpRequestDto.getRole())
                .registeredAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        try {
            userRepository.save(user);
            log.info("[signUp] 새로운 사용자 등록: {} ({})", user.getEmail(), user.getRole());
        } catch (DataIntegrityViolationException e) {
            log.error("[signUp] 중복된 이메일 주소로 인한 가입 거부: {}", signUpRequestDto.getEmail());
            throw new EmailDuplicationException();
        }

        Authentication authentication = createAuthentication(user);
        AuthenticationToken authenticationToken = tokenProvider.generateToken(authentication);

        return GlobalResponse.ok(createTokenResponse(user, authenticationToken));
    }

    public GlobalResponse<TokenResponseDto> signIn(SignInRequestDto signInRequestDto) {
        User user = getUserEntity(signInRequestDto.getEmail());
        validatePassword(signInRequestDto.getPassword(), user.getPassword());

        Authentication authentication = createAuthentication(user);
        AuthenticationToken authenticationToken = tokenProvider.generateToken(authentication);

        return GlobalResponse.ok(createTokenResponse(user, authenticationToken));
    }

    public GlobalResponse<TokenResponseDto> reissue(ReissueRequestDto reissueRequestDto) {
        String refreshToken = reissueRequestDto.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            log.error("[reissue] 유효하지 않은 Refresh Token으로 재발급 시도");
            throw new TokenInvalidException();
        }

        String email = tokenProvider.getUsername(refreshToken);
        if (!tokenProvider.isStoredRefreshToken(email, refreshToken)) {
            log.error("[reissue] 저장된 Refresh Token과 불일치하여 재발급 거부: {}", email);
            throw new TokenInvalidException();
        }

        User user = getUserEntity(email);

        Authentication authentication = createAuthentication(user);
        AuthenticationToken authenticationToken = tokenProvider.generateToken(authentication);
        log.info("[reissue] 토큰 재발급 완료: {}", email);

        return GlobalResponse.ok(createTokenResponse(user, authenticationToken));
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
