package community.likelion.growwithusserver.game.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.game.data.dto.BadgeResponseDto;
import community.likelion.growwithusserver.game.service.BadgeService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users/me/badges")
@RequiredArgsConstructor
public class BadgeController {
    private final BadgeService badgeService;

    @GetMapping
    @Operation(summary = "내 배지 목록")
    public GlobalResponse<List<BadgeResponseDto>> getMyBadges() {
        return badgeService.getMyBadges(SecurityUtil.getUsername());
    }
}
