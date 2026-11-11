package community.likelion.growwithusserver.notification.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationReadResponseDto {
    // 이번 요청으로 읽음 처리된 알림 수
    private int readCount;
    // 남은 안 읽은 알림 수
    private long unreadCount;
}
