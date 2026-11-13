package community.likelion.growwithusserver.garden.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.garden.data.dto.CheerResponseDto;
import community.likelion.growwithusserver.garden.data.dto.GardenHomeResponseDto;
import community.likelion.growwithusserver.garden.service.GardenHomeService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gardens/{gardenId}")
@RequiredArgsConstructor
public class GardenHomeController {
    private final GardenHomeService gardenHomeService;

    @GetMapping("/home")
    @Operation(summary = "홈 한 번에 조회 (캐릭터, 날씨, 스트릭, XP, D-day, 최근 돌봄)")
    public GlobalResponse<GardenHomeResponseDto> getHome(@PathVariable String gardenId) {
        return gardenHomeService.getHome(SecurityUtil.getUsername(), gardenId);
    }

    @PostMapping("/cheers")
    @Operation(summary = "오늘의 응원 (하루 1회, 스트릭과 XP)")
    public GlobalResponse<CheerResponseDto> cheer(@PathVariable String gardenId) {
        return gardenHomeService.cheer(SecurityUtil.getUsername(), gardenId);
    }
}
