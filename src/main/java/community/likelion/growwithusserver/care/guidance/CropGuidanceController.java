package community.likelion.growwithusserver.care.guidance;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/internal/crops/{cropId}/task-guidance")
public class CropGuidanceController {
    private final CropGuidanceService service;
    @GetMapping public GlobalResponse<CropGuidanceService.Update> get(@PathVariable String cropId) {
        return GlobalResponse.ok(service.get(IdPrefix.CROP.parse(cropId)));
    }
    @PutMapping public GlobalResponse<CropGuidanceService.Update> put(@PathVariable String cropId, @RequestBody CropGuidanceService.Update update) {
        return GlobalResponse.ok(service.replace(IdPrefix.CROP.parse(cropId), update));
    }
}
