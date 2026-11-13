package community.likelion.growwithusserver.game.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.game.data.dto.LeagueResponseDto;
import community.likelion.growwithusserver.game.service.LeagueService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/leagues")
@RequiredArgsConstructor
public class LeagueController {
    private final LeagueService leagueService;

    @GetMapping("/current")
    @Operation(summary = "이웃 텃밭 주간 리그 (이번 주 순위)")
    public GlobalResponse<LeagueResponseDto> getCurrent() {
        return leagueService.getCurrent(SecurityUtil.getUsername());
    }
}
