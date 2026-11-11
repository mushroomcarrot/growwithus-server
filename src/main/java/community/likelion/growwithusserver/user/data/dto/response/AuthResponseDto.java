package community.likelion.growwithusserver.user.data.dto.response;

import community.likelion.growwithusserver.user.data.enums.NextStep;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {
    private String userId;
    private String accessToken;
    private String refreshToken;
    // Access Token 만료까지 남은 시간 (초)
    private long expiresIn;
    private UserRole role;
    private NextStep nextStep;
}
