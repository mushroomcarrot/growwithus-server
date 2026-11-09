package community.likelion.growwithusserver.common.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthenticationToken {
    private String accessToken;
    private String refreshToken;
}
