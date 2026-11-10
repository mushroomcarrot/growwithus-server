package community.likelion.growwithusserver.user.data.dto.request;

import community.likelion.growwithusserver.user.data.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleChangeRequestDto {
    // OWNER, CARETAKER만 허용한다. 그 외 값은 서비스에서 C002로 거른다.
    @NotNull
    private UserRole role;
}
