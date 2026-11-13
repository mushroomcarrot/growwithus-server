package community.likelion.growwithusserver.game.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeResponseDto {
    private String code;
    private String name;
    private String description;
    private OffsetDateTime acquiredAt;
}
