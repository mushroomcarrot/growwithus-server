package community.likelion.growwithusserver.user.data.dto.request;

import community.likelion.growwithusserver.user.data.enums.PushPlatform;
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
public class PushTokenRequestDto {
    @NotBlank
    @Size(max = 512)
    private String token;

    @NotNull
    private PushPlatform platform;
}
