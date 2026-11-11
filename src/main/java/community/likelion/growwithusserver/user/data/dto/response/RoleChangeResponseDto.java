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
public class RoleChangeResponseDto {
    private UserRole role;
    private String accessToken;
    private String refreshToken;
    private NextStep nextStep;
}
