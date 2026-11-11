package community.likelion.growwithusserver.common.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthenticationToken {
    private String accessToken;
    private String refreshToken;
    // Access Token 만료까지 남은 시간 (초)
    private long expiresIn;
}
