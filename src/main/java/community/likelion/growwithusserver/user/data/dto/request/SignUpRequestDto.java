package community.likelion.growwithusserver.user.data.dto.request;

import jakarta.validation.constraints.Email;
import community.likelion.growwithusserver.user.data.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String name;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotNull
    private UserRole role;
}
