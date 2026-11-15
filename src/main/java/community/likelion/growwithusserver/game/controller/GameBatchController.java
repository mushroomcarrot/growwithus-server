package community.likelion.growwithusserver.game.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.game.data.dto.LeagueSettleRequestDto;
import community.likelion.growwithusserver.game.data.dto.LeagueSettleResponseDto;
import community.likelion.growwithusserver.game.data.dto.StreakCheckRequestDto;
import community.likelion.growwithusserver.game.data.dto.StreakCheckResponseDto;
import community.likelion.growwithusserver.game.service.LeagueSettleService;
import community.likelion.growwithusserver.game.service.StreakService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// X-Service-Key 헤더 검증은 InternalServiceKeyFilter와 SecurityConfiguration이 한다.
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class GameBatchController {
    private final StreakService streakService;
    private final LeagueSettleService leagueSettleService;

    @PostMapping("/streaks/check")
    @Operation(summary = "스트릭 끊김 처리 (전날 응원이 없는 텃밭)")
    public GlobalResponse<StreakCheckResponseDto> checkStreaks(@RequestBody(required = false) StreakCheckRequestDto streakCheckRequestDto) {
        return GlobalResponse.ok(streakService.check(streakCheckRequestDto == null ? null : streakCheckRequestDto.getDate()));
    }

    @PostMapping("/streaks/remind")
    @Operation(summary = "스트릭 위험 알림 (오늘 응원이 없는 주인)")
    public GlobalResponse<community.likelion.growwithusserver.game.data.dto.StreakRemindResponseDto> remindStreaks() {
        return GlobalResponse.ok(streakService.remind());
    }

    @PostMapping("/leagues/settle")
    @Operation(summary = "주간 리그 정산, 새 리그와 퀘스트 생성")
    public GlobalResponse<LeagueSettleResponseDto> settleLeagues(@RequestBody(required = false) LeagueSettleRequestDto leagueSettleRequestDto) {
        return GlobalResponse.ok(leagueSettleService.settle(leagueSettleRequestDto == null ? null : leagueSettleRequestDto.getWeekStart()));
    }
}
