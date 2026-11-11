package community.likelion.growwithusserver.notification.controller;

import community.likelion.growwithusserver.common.pagination.CursorPageRequest;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.notification.data.dto.NotificationListResponseDto;
import community.likelion.growwithusserver.notification.data.dto.NotificationReadResponseDto;
import community.likelion.growwithusserver.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 목록 (최신순, 커서 페이지네이션)")
    public GlobalResponse<NotificationListResponseDto> getNotifications(@Valid @ModelAttribute CursorPageRequest pageRequest) {
        return notificationService.getNotifications(SecurityUtil.getUsername(), pageRequest);
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 (notificationId 자리에 all을 넣으면 모두 읽음)")
    public GlobalResponse<NotificationReadResponseDto> markRead(@PathVariable String notificationId) {
        return notificationService.markRead(SecurityUtil.getUsername(), notificationId);
    }
}
