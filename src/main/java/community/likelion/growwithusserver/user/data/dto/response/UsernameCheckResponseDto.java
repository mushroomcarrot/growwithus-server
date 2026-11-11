package community.likelion.growwithusserver.user.data.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsernameCheckResponseDto {
    private String username;
    private boolean available;
}
