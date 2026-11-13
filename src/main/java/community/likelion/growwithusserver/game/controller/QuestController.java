package community.likelion.growwithusserver.game.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.game.data.dto.QuestClaimResponseDto;
import community.likelion.growwithusserver.game.data.dto.QuestResponseDto;
import community.likelion.growwithusserver.game.service.QuestService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/quests")
@RequiredArgsConstructor
public class QuestController {
    private final QuestService questService;

    @GetMapping("/weekly")
    @Operation(summary = "주간 퀘스트와 진행도")
    public GlobalResponse<List<QuestResponseDto>> getWeekly() {
        return questService.getWeekly(SecurityUtil.getUsername());
    }

    @PostMapping("/{questId}/claim")
    @Operation(summary = "퀘스트 보상 받기 (달성한 퀘스트, 한 번만)")
    public GlobalResponse<QuestClaimResponseDto> claim(@PathVariable String questId) {
        return questService.claim(SecurityUtil.getUsername(), questId);
    }
}
