package community.likelion.growwithusserver.user.service;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.AuthenticationToken;
import community.likelion.growwithusserver.common.security.JwtTokenProvider;
import community.likelion.growwithusserver.common.security.exception.TokenInvalidException;
import community.likelion.growwithusserver.user.data.dto.request.RoleChangeRequestDto;
import community.likelion.growwithusserver.user.data.dto.response.RoleChangeResponseDto;
import community.likelion.growwithusserver.user.data.dto.response.UserMeResponseDto;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.NextStep;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import community.likelion.growwithusserver.user.data.exception.RoleNotAllowedException;
import community.likelion.growwithusserver.user.data.exception.UserNotFoundException;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.user.data.repository.UserRepository;
import community.likelion.growwithusserver.user.profile.OwnerSummaryReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final OwnerSummaryReader ownerSummaryReader;

    @Transactional(readOnly = true)
    public User getUserEntity(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("[getUserEntity] 사용자 조회 실패: {}", username);
                    return new UserNotFoundException();
                });
    }

    // nextStep 계산은 여기 한 곳에서만 한다. signup, login, /users/me, 역할 변경이 모두 이 메서드를 쓴다.
    public NextStep resolveNextStep(User user) {
        return switch (user.getRole()) {
            case USER -> NextStep.ROLE_SELECT;
            case OWNER -> user.isProfileRegistered() ? NextStep.OWNER_HOME : NextStep.PROFILE_OWNER;
            case CARETAKER -> user.isProfileRegistered() ? NextStep.CARE_TODAY : NextStep.PROFILE_CARE;
            // ADMIN은 앱 화면 흐름이 없다
            case ADMIN -> null;
        };
    }

    // 사용자의 현재 역할을 authorities claim에 담아 AT, RT를 새로 발급한다.
    public AuthenticationToken issueToken(User user) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority(user.getRole().getValue()))
        );

        return tokenProvider.generateToken(authentication);
    }

    @Transactional(readOnly = true)
    public GlobalResponse<UserMeResponseDto> getMe(String username) {
        User user = getUserEntity(username);
        UserMeResponseDto.OwnerSummary owner = null;
        if (user.getRole() == UserRole.OWNER || user.getRole() == UserRole.ADMIN) {
            owner = ownerSummaryReader.findByUsername(username)
                    .map(summary -> UserMeResponseDto.OwnerSummary.builder()
                            .nickname(summary.nickname())
                            .district(summary.district())
                            .gardenId(IdPrefix.GARDEN.format(summary.gardenId()))
                            .build())
                    .orElse(null);
        }

        return GlobalResponse.ok(UserMeResponseDto.builder()
                .userId(user.getPublicId())
                .name(user.getName())
                .username(user.getUsername())
                .role(user.getRole())
                .nextStep(resolveNextStep(user))
                .owner(owner)
                .caretaker(null)
                .build());
    }

    @Transactional
    public GlobalResponse<RoleChangeResponseDto> changeRole(String username, String bearerToken,
                                                            RoleChangeRequestDto roleChangeRequestDto) {
        UserRole newRole = roleChangeRequestDto.getRole();
        if (newRole != UserRole.OWNER && newRole != UserRole.CARETAKER) {
            log.error("[changeRole] 허용되지 않는 역할로 변경 시도: {} ({})", username, newRole);
            throw new RoleNotAllowedException();
        }

        String accessToken = tokenProvider.resolveToken(bearerToken);
        if (accessToken == null) {
            throw new TokenInvalidException();
        }

        User user = getUserEntity(username);
        if (user.getRole() != newRole) {
            log.info("[changeRole] 역할 변경: {} ({} > {})", username, user.getRole(), newRole);
            user.setRole(newRole);
            // 프로필은 역할별로 따로 있다. 새 역할의 프로필을 이미 등록했으면 그대로 쓰고, 없으면 다시 등록해야 한다
            user.setProfileRegistered(newRole == UserRole.OWNER && ownerSummaryReader.findByUsername(username).isPresent());
        }

        // 이전 역할이 담긴 토큰을 무효로 만들고 새 역할로 다시 발급한다
        tokenProvider.invalidateToken(accessToken);
        AuthenticationToken authenticationToken = issueToken(user);

        return GlobalResponse.ok(RoleChangeResponseDto.builder()
                .role(user.getRole())
                .accessToken(authenticationToken.getAccessToken())
                .refreshToken(authenticationToken.getRefreshToken())
                .nextStep(resolveNextStep(user))
                .build());
    }
}
