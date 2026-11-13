package community.likelion.growwithusserver.evolution.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.evolution.data.dto.EventAckResponseDto;
import community.likelion.growwithusserver.evolution.data.dto.EvolutionEventResponseDto;
import community.likelion.growwithusserver.evolution.service.EvolutionEventService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EvolutionEventController {
    private final EvolutionEventService evolutionEventService;

    @GetMapping("/gardens/{gardenId}/events")
    @Operation(summary = "미확인 진화 이벤트")
    public GlobalResponse<List<EvolutionEventResponseDto>> getUnackedEvents(@PathVariable String gardenId) {
        return evolutionEventService.getUnackedEvents(SecurityUtil.getUsername(), gardenId);
    }

    @PostMapping("/events/{eventId}/ack")
    @Operation(summary = "진화 이벤트 확인과 보상 지급 (한 번만)")
    public GlobalResponse<EventAckResponseDto> ack(@PathVariable String eventId) {
        return evolutionEventService.ack(SecurityUtil.getUsername(), eventId);
    }
}
