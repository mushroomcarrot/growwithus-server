package community.likelion.growwithusserver.notification.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 커서 페이지 응답(items, nextCursor)에 안 읽은 알림 수를 더한 형태
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListResponseDto {
    private List<NotificationItemDto> items;
    private long unreadCount;
    private String nextCursor;
}
