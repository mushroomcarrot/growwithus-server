package community.likelion.growwithusserver.growth.controller;
import community.likelion.growwithusserver.growth.service.GrowthService;
import community.likelion.growwithusserver.growth.data.dto.GrowthDtos;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class GrowthController {
    private final GrowthService growth;
    @GetMapping("/gardens/{gardenId}/growth")
    public GlobalResponse<GrowthDtos.Detail> get(@PathVariable String gardenId) {
        return GlobalResponse.ok(growth.get(SecurityUtil.getUsername(), IdPrefix.GARDEN.parse(gardenId)));
    }
    @PostMapping("/gardens/{gardenId}/stage-checks")
    public GlobalResponse<GrowthDtos.Checked> check(@PathVariable String gardenId, @Valid @RequestBody GrowthDtos.CheckRequest request) {
        return GlobalResponse.ok(growth.check(SecurityUtil.getUsername(), IdPrefix.GARDEN.parse(gardenId), request));
    }
}
